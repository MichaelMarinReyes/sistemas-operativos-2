package com.sopes.backendproyectofinal.inventory;

/**
 * Existencias de un producto: cuántas unidades hay físicamente, cuántas están comprometidas con
 * pedidos y cuántas siguen libres.
 *
 * Las unidades pendientes ya son existencias, pero no se pueden reservar hasta encontrar ubicación.
 */
public record InventoryItem(String productId, int stock, int reserved, int pendingPlacement) {

    public InventoryItem {
        if (stock < 0 || reserved < 0 || pendingPlacement < 0 || reserved > stock - pendingPlacement) {
            throw new IllegalArgumentException("Las existencias, reservas y unidades pendientes no son coherentes.");
        }
    }

    /** Unidades que un pedido nuevo podría reservar ahora mismo. */
    public int available() { return stock - reserved - pendingPlacement; }

    /** Coherencia interna: nada comprometido por encima de lo existente. */
    public boolean isConsistent() { return reserved <= stock - pendingPlacement; }
}
