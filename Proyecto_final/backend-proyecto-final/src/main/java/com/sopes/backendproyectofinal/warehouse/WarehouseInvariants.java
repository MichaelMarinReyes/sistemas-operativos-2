package com.sopes.backendproyectofinal.warehouse;

import com.sopes.backendproyectofinal.catalog.Product;
import java.util.List;
import java.util.Map;

/**
 * Comprobaciones de las propiedades que el resto del sistema da por ciertas del almacén.
 *
 * No sustituyen a las pruebas de concurrencia del servicio de inventario: aquí se demuestra que el
 * algoritmo de asignación es correcto por sí solo, con una sola hebra. Las pruebas que importan para
 * el análisis de concurrencia son las de reserva simultánea, esas están en el módulo de inventario.
 */
public final class WarehouseInvariants {

    private WarehouseInvariants() {}

    /** El volumen ocupado nunca excede la capacidad total ni la de ninguna ubicación. */
    public static void checkVolume(Warehouse warehouse, List<StorageLocation> locations) {
        int total = locations.stream().mapToInt(StorageLocation::usedVolume).sum();
        if (total != warehouse.occupiedVolume()) {
            throw new IllegalStateException("El volumen ocupado no coincide con la suma de las ubicaciones.");
        }
        for (StorageLocation location : locations) {
            if (location.usedVolume() > location.capacity()) {
                throw new IllegalStateException("La ubicación " + location.id() + " excede su capacidad.");
            }
        }
    }

    /** Cada ubicación contiene el producto que el detalle declara y solo unidades enteras. */
    public static void checkStoredUnits(Warehouse warehouse, Map<String, Product> catalog) {
        warehouse.unitsByProduct().forEach((productId, byLocation) -> {
            if (!catalog.containsKey(productId)) {
                throw new IllegalStateException("Hay unidades de un producto desconocido: " + productId);
            }
            byLocation.forEach((locationId, units) -> {
                if (units <= 0) {
                    throw new IllegalStateException("Hay una ubicación con unidades no positivas.");
                }
                if (warehouse.unitsAt(locationId, productId) != units) {
                    throw new IllegalStateException("El detalle por ubicación no coincide con las existencias.");
                }
            });
        });
    }

    /** Cada lote ocupa un volumen y una cantidad de unidades coherentes con sus partes. */
    public static void checkLots(List<Lot> lots) {
        for (Lot lot : lots) {
            int units = lot.allocations().stream().mapToInt(LotAllocation::units).sum();
            int volume = lot.allocations().stream().mapToInt(LotAllocation::usedVolume).sum();
            if (units != lot.units() || volume != lot.usedVolume()) {
                throw new IllegalStateException("El lote " + lot.id() + " no cuadra con sus partes.");
            }
        }
    }
}
