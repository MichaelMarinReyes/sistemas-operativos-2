package com.sopes.backendproyectofinal.inventory;

/** Mensaje inmutable: recepción entrega cantidades, nunca referencias al almacén. */
public record StockReceipt(String id, String productId, int units) {
    public StockReceipt {
        if (id == null || id.isBlank() || productId == null || productId.isBlank() || units <= 0) {
            throw new IllegalArgumentException("El mensaje de recepción está incompleto.");
        }
    }
}
