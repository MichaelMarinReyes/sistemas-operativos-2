package com.sopes.backendproyectofinal.orders;

/**
 * Transiciones permitidas de un pedido.
 *
 * QUEUED vuelve a WAITING_STOCK cuando el trabajador pierde la carrera de la reserva: otro pedido
 * se llevó las últimas unidades entre la comprobación de elegibilidad y la reserva efectiva.
 */
public enum OrderStatus {
    CREATED, WAITING_STOCK, QUEUED, WAITING_RESOURCES, PROCESSING, DEADLOCKED, COMPLETED, ERROR;

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null || next == this || this == COMPLETED || this == ERROR) return false;
        if (next == ERROR) return true;
        return switch (this) {
            case CREATED -> next == WAITING_STOCK || next == QUEUED;
            case WAITING_STOCK -> next == QUEUED;
            case QUEUED -> next == PROCESSING || next == WAITING_RESOURCES || next == WAITING_STOCK;
            case WAITING_RESOURCES -> next == QUEUED || next == PROCESSING || next == DEADLOCKED;
            case PROCESSING -> next == WAITING_RESOURCES || next == DEADLOCKED || next == COMPLETED;
            case DEADLOCKED -> next == QUEUED || next == WAITING_RESOURCES || next == PROCESSING;
            default -> false;
        };
    }
}
