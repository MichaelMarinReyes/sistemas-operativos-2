package com.sopes.backendproyectofinal.warehouse;

/**
 * Ubicación física identificada por pasillo, estante y nivel.
 *
 * Es una vista inmutable: el almacén mutable mantiene su propio estado y publica copias de este
 * registro para que ninguna ruta de lectura observe un objeto compartido mientras se modifica.
 */
public record StorageLocation(String id, String label, int capacity, int usedVolume) {

    public StorageLocation {
        if (capacity < 1) {
            throw new IllegalArgumentException("La capacidad de una ubicación debe ser positiva.");
        }
        if (usedVolume < 0 || usedVolume > capacity) {
            throw new IllegalArgumentException("El volumen ocupado no cabe en la ubicación.");
        }
    }

    /** Volumen todavía disponible de la ubicación. */
    public int availableVolume() {
        return capacity - usedVolume;
    }
}
