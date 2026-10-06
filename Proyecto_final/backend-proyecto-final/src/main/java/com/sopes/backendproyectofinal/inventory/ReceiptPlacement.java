package com.sopes.backendproyectofinal.inventory;

/** Confirmación del inventario; las pendientes siguen perteneciendo al mismo mensaje. */
public record ReceiptPlacement(StockReceipt message, int pendingUnits) {
    public int placedUnits() { return message.units() - pendingUnits; }
}
