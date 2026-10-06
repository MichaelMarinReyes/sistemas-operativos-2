package com.sopes.backendproyectofinal.inventory;

import java.time.Instant;
import java.util.List;

/**
 * Compromiso de existencias para un pedido, con la ubicación de la que sale cada línea.
 *
 * Es inmutable y se construye entero o no se construye: si una sola línea falla, no existe reserva y
 * el pedido vuelve a esperar existencias. Guardar además de qué ubicación salía cada línea es lo que
 * permite devolver exactamente las mismas unidades si el pedido falla antes de salir del almacén.
 */
public record StockReservation(String orderId, List<ReservationLine> lines, Instant createdAt) {

    public StockReservation {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Una reserva debe tener al menos una línea.");
        }
    }

    public int totalUnits() {
        return lines.stream().mapToInt(ReservationLine::units).sum();
    }

    /** Una línea reservada y la ubicación concreta que respalda esas unidades. */
    public record ReservationLine(String productId, int units, String locationId, int unitVolume) {
        public ReservationLine {
            if (units < 1) {
                throw new IllegalArgumentException("Una línea reservada debe tener unidades.");
            }
        }
    }
}
