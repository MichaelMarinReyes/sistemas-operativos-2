package com.sopes.backendproyectofinal.orders;

import com.sopes.backendproyectofinal.shared.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderLifecycleTests {
    @Test
    void supportsStockWaitAndDeadlockRecoveryWithoutSkippingCompletionRules() {
        Order created = new Order("order", "customer", "Ana", 3, List.of(), 0, OrderStatus.CREATED, Instant.now());
        assertThrows(ApiException.class, () -> created.transitionTo(OrderStatus.COMPLETED));
        Order completed = created.transitionTo(OrderStatus.WAITING_STOCK)
                .transitionTo(OrderStatus.QUEUED)
                .transitionTo(OrderStatus.WAITING_RESOURCES)
                .transitionTo(OrderStatus.DEADLOCKED)
                .transitionTo(OrderStatus.QUEUED)
                .transitionTo(OrderStatus.PROCESSING)
                .transitionTo(OrderStatus.COMPLETED);
        assertEquals(OrderStatus.CREATED, created.status());
        assertEquals(created.id(), completed.id());
        assertThrows(ApiException.class, () -> completed.transitionTo(OrderStatus.PROCESSING));
        assertThrows(ApiException.class, () -> created.transitionTo(OrderStatus.ERROR).transitionTo(OrderStatus.QUEUED));
    }
}
