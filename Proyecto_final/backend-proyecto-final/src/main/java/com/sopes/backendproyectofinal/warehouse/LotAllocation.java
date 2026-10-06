package com.sopes.backendproyectofinal.warehouse;

/**
 * Parte de un lote ubicada físicamente en una posición concreta.
 *
 * La suma de unidades de todas las partes de un lote es su cantidad total, y la suma de volumen
 * coincide con el que esas unidades aportan a la ubicación donde están.
 */
public record LotAllocation(String locationId, int units, int usedVolume) {

    public LotAllocation {
        if (units < 1) {
            throw new IllegalArgumentException("Cada parte de un lote debe tener al menos una unidad.");
        }
        if (usedVolume < 1) {
            throw new IllegalArgumentException("Cada parte de un lote debe ocupar volumen.");
        }
    }
}