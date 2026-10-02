package com.sopes.backendproyectofinal.orders;

import com.sopes.backendproyectofinal.shared.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Conserva una copia del nivel contratado y las líneas al registrar el pedido. */
public record Order(String id, String customerId, String customerName, int serviceLevel,
                    List<OrderLine> lines, int totalUnits, OrderStatus status, Instant createdAt,
                    OrderStage stage, double progress, Double estimatedRemainingMinutes,
                    Instant startedAt, Instant completedAt, String errorMessage) {
    public Order {
        lines = List.copyOf(lines);
    }

    public Order(String id, String customerId, String customerName, int serviceLevel,
                 List<OrderLine> lines, int totalUnits, OrderStatus status, Instant createdAt) {
        this(id, customerId, customerName, serviceLevel, lines, totalUnits, status, createdAt,
                OrderStage.NOT_STARTED, 0, null, null, null, null);
    }

    public Order transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "El cambio de estado del pedido no está permitido.");
        }
        return new Order(id, customerId, customerName, serviceLevel, lines, totalUnits, next, createdAt,
                next == OrderStatus.COMPLETED ? OrderStage.COMPLETED : stage,
                next == OrderStatus.COMPLETED ? 100 : progress,
                next == OrderStatus.COMPLETED ? Double.valueOf(0) : next == OrderStatus.ERROR ? null : estimatedRemainingMinutes,
                startedAt, next == OrderStatus.COMPLETED ? Instant.now() : completedAt, errorMessage);
    }

    public Order advance(OrderStage nextStage, double percentage, Double remainingMinutes) {
        return new Order(id, customerId, customerName, serviceLevel, lines, totalUnits, status, createdAt,
                nextStage, percentage, remainingMinutes,
                startedAt == null && status == OrderStatus.PROCESSING ? Instant.now() : startedAt,
                completedAt, errorMessage);
    }

    public Order fail(String message) {
        Order failed = transitionTo(OrderStatus.ERROR);
        return new Order(id, customerId, customerName, serviceLevel, lines, totalUnits, failed.status(), createdAt,
                stage, progress, null, startedAt, completedAt, message);
    }
}
