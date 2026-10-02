package com.sopes.backendproyectofinal.orders;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.function.UnaryOperator;
import com.sopes.backendproyectofinal.shared.SimulationState;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
    private final Map<String, Order> orders = new HashMap<>();
    private final SimulationState state;
    private final Condition orderAvailable;
    private long revision;

    public OrderRepository(SimulationState state) {
        this.state = state;
        orderAvailable = state.lock.newCondition();
    }

    public void save(Order order) {
        state.lock.lock();
        try { orders.put(order.id(), order); revision++; orderAvailable.signalAll(); }
        finally { state.lock.unlock(); }
    }

    public Optional<Order> findById(String id) {
        state.lock.lock();
        try { return Optional.ofNullable(orders.get(id)); }
        finally { state.lock.unlock(); }
    }

    /** Selección y transición atómicas: dos trabajadores nunca retiran el mismo pedido. */
    public Order awaitNext() throws InterruptedException {
        state.lock.lockInterruptibly();
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                Optional<Order> next = orders.values().stream().filter(order -> order.status() == OrderStatus.CREATED)
                        .min(Comparator.comparingInt(Order::serviceLevel).thenComparing(Order::createdAt).thenComparing(Order::id));
                if (next.isPresent()) {
                    Order queued = next.get().transitionTo(OrderStatus.QUEUED);
                    orders.put(queued.id(), queued);
                    revision++;
                    return queued;
                }
                orderAvailable.await();
            }
        } finally { state.lock.unlock(); }
    }

    public void update(String id, UnaryOperator<Order> change) {
        state.lock.lock();
        try {
            Order previous = orders.get(id);
            Order updated = change.apply(previous);
            orders.put(id, updated);
            if (previous.status() != updated.status()) revision++;
        } finally { state.lock.unlock(); }
    }

    public long revision() {
        state.lock.lock();
        try { return revision; }
        finally { state.lock.unlock(); }
    }

    /** Devuelve una copia ordenada, sin exponer la colección mutable compartida. */
    public List<Order> findAll() {
        state.lock.lock();
        try { return orders.values().stream()
                .sorted(Comparator.comparing(Order::createdAt).reversed().thenComparing(Order::id)).toList();
        } finally { state.lock.unlock(); }
    }
}
