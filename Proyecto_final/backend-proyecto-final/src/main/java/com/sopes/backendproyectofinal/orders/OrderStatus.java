package com.sopes.backendproyectofinal.orders;

/** Transiciones permitidas; el motor de los próximos hitos será quien las ejecute. */
public enum OrderStatus {
    CREATED, WAITING_STOCK, QUEUED, WAITING_RESOURCES, PROCESSING, DEADLOCKED, COMPLETED, ERROR;

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null || next == this || this == COMPLETED || this == ERROR) return false;
        if (next == ERROR) return true;
        return switch (this) {
            case CREATED -> next == WAITING_STOCK || next == QUEUED;
            case WAITING_STOCK -> next == QUEUED;
            case QUEUED -> next == PROCESSING || next == WAITING_RESOURCES;
            case WAITING_RESOURCES -> next == QUEUED || next == PROCESSING || next == DEADLOCKED;
            case PROCESSING -> next == WAITING_RESOURCES || next == DEADLOCKED || next == COMPLETED;
            case DEADLOCKED -> next == QUEUED || next == WAITING_RESOURCES || next == PROCESSING;
            default -> false;
        };
    }
}
