package com.sopes.backendproyectofinal.resources;

import com.sopes.backendproyectofinal.shared.SimulationState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import org.springframework.stereotype.Component;

@Component
public class ResourceManager {
    private final SimulationState state;
    private final Condition available;
    private final Map<ResourceType, Semaphore> permits = new EnumMap<>(ResourceType.class);
    private final Map<ResourceType, List<ResourceInstance>> instances = new EnumMap<>(ResourceType.class);
    private final Map<String, Map<ResourceType, Integer>> requests = new LinkedHashMap<>();

    public ResourceManager(SimulationState state) {
        this.state = state;
        available = state.lock.newCondition();
        for (ResourceType type : ResourceType.values()) {
            permits.put(type, new Semaphore(type.capacity(), true));
            List<ResourceInstance> entries = new ArrayList<>();
            for (int index = 1; index <= type.capacity(); index++) {
                entries.add(new ResourceInstance(type.name() + "-" + index, null, null));
            }
            instances.put(type, entries);
        }
    }

    /** Adquiere el conjunto completo o revierte permisos parciales antes de esperar. */
    public Lease acquire(String operationId, Map<ResourceType, Integer> required) throws InterruptedException {
        Map<ResourceType, Integer> ordered = new EnumMap<>(ResourceType.class);
        ordered.putAll(required);
        ordered.forEach((type, count) -> {
            if (count == null || count < 1 || count > type.capacity()) {
                throw new IllegalArgumentException("La solicitud excede la capacidad del recurso.");
            }
        });
        state.lock.lockInterruptibly();
        try {
            requests.put(operationId, Map.copyOf(ordered));
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                List<ResourceType> acquired = new ArrayList<>();
                for (var entry : ordered.entrySet()) {
                    if (!permits.get(entry.getKey()).tryAcquire(entry.getValue())) break;
                    acquired.add(entry.getKey());
                }
                if (acquired.size() == ordered.size()) {
                    Instant now = Instant.now();
                    ordered.forEach((type, count) -> {
                        List<ResourceInstance> entries = instances.get(type);
                        int remaining = count;
                        for (int index = 0; index < entries.size() && remaining > 0; index++) {
                            ResourceInstance entry = entries.get(index);
                            if (entry.ownerId() == null) {
                                entries.set(index, new ResourceInstance(entry.id(), operationId, now));
                                remaining--;
                            }
                        }
                    });
                    return new Lease(operationId, ordered);
                }
                acquired.forEach(type -> permits.get(type).release(ordered.get(type)));
                // await libera el bloqueo compartido; no se retiene ningún permiso mientras se espera.
                available.await();
            }
        } finally {
            requests.remove(operationId);
            state.lock.unlock();
        }
    }

    public List<ResourceSummary> snapshot() {
        state.lock.lock();
        try {
            return instances.entrySet().stream().map(entry -> new ResourceSummary(entry.getKey(),
                    entry.getKey().label(), entry.getKey().capacity(),
                    entry.getKey().capacity() - permits.get(entry.getKey()).availablePermits(),
                    List.copyOf(entry.getValue()))).toList();
        } finally { state.lock.unlock(); }
    }

    public List<ResourceRequest> pendingRequests() {
        state.lock.lock();
        try {
            return requests.entrySet().stream().map(entry -> new ResourceRequest(entry.getKey(), entry.getValue())).toList();
        } finally { state.lock.unlock(); }
    }

    /** La liberación idempotente pertenece al trabajador y se utiliza con try-with-resources. */
    public final class Lease implements AutoCloseable {
        private final String ownerId;
        private final Map<ResourceType, Integer> allocation;
        private boolean closed;

        private Lease(String ownerId, Map<ResourceType, Integer> allocation) {
            this.ownerId = ownerId;
            this.allocation = Map.copyOf(allocation);
        }

        @Override
        public void close() {
            state.lock.lock();
            try {
                if (closed) return;
                allocation.forEach((type, count) -> {
                    List<ResourceInstance> entries = instances.get(type);
                    entries.replaceAll(entry -> ownerId.equals(entry.ownerId())
                            ? new ResourceInstance(entry.id(), null, null) : entry);
                    permits.get(type).release(count);
                });
                closed = true;
                available.signalAll();
            } finally { state.lock.unlock(); }
        }
    }

    public record ResourceInstance(String id, String ownerId, Instant acquiredAt) {}
    public record ResourceSummary(ResourceType type, String name, int capacity, int inUse, List<ResourceInstance> instances) {}
    public record ResourceRequest(String orderId, Map<ResourceType, Integer> required) {}
}
