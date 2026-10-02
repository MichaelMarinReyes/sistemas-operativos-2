package com.sopes.backendproyectofinal.status;

import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.orders.OrderRepository;
import com.sopes.backendproyectofinal.orders.OrderService;
import com.sopes.backendproyectofinal.orders.OrderStatus;
import com.sopes.backendproyectofinal.resources.ResourceManager;
import com.sopes.backendproyectofinal.shared.SimulationState;
import com.sopes.backendproyectofinal.simulation.SimulationClock;
import com.sopes.backendproyectofinal.simulation.SimulationEngine;
import com.sopes.backendproyectofinal.simulation.SimulationSettings;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StatusService {
    private final OrderService orders;
    private final OrderRepository repository;
    private final ResourceManager resources;
    private final SimulationEngine engine;
    private final SimulationClock clock;
    private final SimulationSettings settings;
    private final SimulationState state;
    private final String runId = UUID.randomUUID().toString();
    private long sequence;

    public StatusService(OrderService orders, OrderRepository repository, ResourceManager resources,
                         SimulationEngine engine, SimulationClock clock, SimulationSettings settings, SimulationState state) {
        this.orders = orders; this.repository = repository; this.resources = resources;
        this.engine = engine; this.clock = clock; this.settings = settings; this.state = state;
    }

    /** Captura inmutable bajo el mismo bloqueo que protege pedidos y asignaciones; nunca envía red aquí. */
    public StatusResponse getStatus() {
        state.lock.lock();
        try {
            List<Order> snapshot = repository.findAll();
            List<Order> active = snapshot.stream().filter(order -> order.status() == OrderStatus.QUEUED
                    || order.status() == OrderStatus.WAITING_RESOURCES || order.status() == OrderStatus.PROCESSING
                    || order.status() == OrderStatus.DEADLOCKED).toList();
            List<Order> waiting = snapshot.stream().filter(order -> order.status() == OrderStatus.CREATED)
                    .sorted(Comparator.comparingInt(Order::serviceLevel).thenComparing(Order::createdAt).thenComparing(Order::id))
                    .limit(50).toList();
            return new StatusResponse(3, runId, ++sequence, Instant.now(), engine.status(), "STRICT_PRIORITY",
                    clock.elapsedMinutes(), settings.getSimulatedSecondsPerRealSecond(), settings.getWorkerCount(),
                    resources.snapshot(), resources.pendingRequests(), new StatusResponse.WarehouseSummary(100, 10, 0),
                    orders.summarize(), repository.revision(), active, waiting);
        } finally { state.lock.unlock(); }
    }
}
