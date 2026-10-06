package com.sopes.backendproyectofinal.inventory;

import java.time.Instant;
import java.util.List;

/**
 * Trazabilidad de las unidades que salieron físicamente del almacén para un pedido.
 *
 * Cuando un pedido falla después de tomar su mercancía, el inventario necesita saber exactamente
 * qué unidades y de qué ubicación se Retireó para devolverlas. Ese registro es lo que convierte una
 * pérdida de mercancía en una operación recuperable y auditable.
 */
public record StockIssue(String orderId, List<IssuedLine> lines, Instant issuedAt) {

    public StockIssue {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Un retiro debe registrar al menos una línea.");
        }
    }

    public int totalUnits() {
        return lines.stream().mapToInt(IssuedLine::units).sum();
    }

    /** Unidades retiradas de una ubicación concreta. */
    public record IssuedLine(String productId, int units, String locationId, int unitVolume) {
        public IssuedLine {
            if (units < 1) {
                throw new IllegalArgumentException("Una línea de retiro debe tener unidades.");
            }
        }
    }
}
