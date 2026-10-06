package com.sopes.backendproyectofinal.warehouse;

import com.sopes.backendproyectofinal.catalog.Product;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Almacén de 100 ubicaciones de 10 unidades de volumen, identificadas por pasillo, estante y nivel.
 *
 * No es seguro entre hilos por sí mismo. Tiene un único propietario, el servicio de inventario, que
 * lo modifica siempre dentro de su propio bloqueo. Declararlo aquí evita que un controlador lo use
 * por su cuenta y rompa el aislamiento del inventario, que es el requisito de comunicación entre
 * áreas del enunciado.
 *
 * La asignación es no contigua: nunca se exige que un lote viva en un tramo seguido de estantes. Las
 * unidades se colocan primero donde ya hay producto del mismo tipo y después en la ubicación libre
 * que mejor ajusta, de modo que un lote queda repartido entre posiciones no contiguas.
 */
public class Warehouse {
    private static final String AISLE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final int locationCapacity;
    private final Map<String, Integer> capacityByLocation = new LinkedHashMap<>();
    private final Map<String, Integer> usedVolumeByLocation = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> unitsByLocationAndProduct = new LinkedHashMap<>();

    /**
     * Crea un almacén de aisles por estantes por niveles. Las letras de pasillo se limitan a las
     * disponibles; si se piden más de 26, los pasillos siguientes se numeran.
     */
    public Warehouse(int locationCapacity, int aisles, int shelvesPerAisle, int levelsPerShelf) {
        if (locationCapacity < 1 || aisles < 1 || shelvesPerAisle < 1 || levelsPerShelf < 1) {
            throw new IllegalArgumentException("Las dimensiones del almacén deben ser positivas.");
        }
        this.locationCapacity = locationCapacity;
        for (int aisleIndex = 0; aisleIndex < aisles; aisleIndex++) {
            String aisle = aisleLabel(aisleIndex);
            for (int shelf = 1; shelf <= shelvesPerAisle; shelf++) {
                for (int level = 1; level <= levelsPerShelf; level++) {
                    String id = aisle + "-" + shelf + "-" + level;
                    capacityByLocation.put(id, locationCapacity);
                    usedVolumeByLocation.put(id, 0);
                    unitsByLocationAndProduct.put(id, new LinkedHashMap<>());
                }
            }
        }
    }

    private static String aisleLabel(int index) {
        return index < AISLE_LETTERS.length() ? String.valueOf(AISLE_LETTERS.charAt(index))
                : String.valueOf(index + 1);
    }

    public int locationCount() {
        return capacityByLocation.size();
    }

    public int totalCapacity() {
        return locationCapacity * capacityByLocation.size();
    }

    public int occupiedVolume() {
        return usedVolumeByLocation.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int availableVolume() {
        return totalCapacity() - occupiedVolume();
    }

    public int locationsInUse() {
        return (int) usedVolumeByLocation.values().stream().filter(volume -> volume > 0).count();
    }

    public StorageLocation location(String id) {
        return new StorageLocation(id, id, locationCapacity, usedVolumeByLocation.get(id));
    }

    /** Copia inmutable de todas las ubicaciones, ordenada por identificador. */
    public List<StorageLocation> locations() {
        return capacityByLocation.keySet().stream().sorted().map(this::location).toList();
    }

    /** Unidades de un producto que están físicamente en una ubicación concreta. */
    public int unitsAt(String locationId, String productId) {
        return unitsByLocationAndProduct.get(locationId).getOrDefault(productId, 0);
    }

    /**
     * Coloca unidades completas de un producto, actualiza el almacén y devuelve las partes del lote.
     *
     * Solo se asignan unidades que caben enteras: una ubicación cuyo volumen libre sea menor que el
     * de una unidad se descarta aunque tenga espacio parcial. Si no cabe nada se devuelve una lista
     * vacía y el almacén queda intacto, para que quien llama pueda dejar la mercancía pendiente de
     * espacio sin perderla.
     */
    public List<LotAllocation> store(Product product, int units) {
        if (units < 1) {
            return List.of();
        }
        Map<String, Integer> placement = new LinkedHashMap<>();
        int placed = placeWhereProductAlreadyLives(product, placement, units);
        if (placed < units) {
            placeInBestFitFreeLocations(product, placement, units - placed);
        }
        if (placement.isEmpty()) {
            return List.of();
        }
        placement.forEach((locationId, quantity) -> {
            usedVolumeByLocation.merge(locationId, quantity * product.unitVolume(), Integer::sum);
            unitsByLocationAndProduct.get(locationId).merge(product.id(), quantity, Integer::sum);
        });
        return placement.entrySet().stream()
                .map(entry -> new LotAllocation(entry.getKey(), entry.getValue(),
                        entry.getValue() * product.unitVolume()))
                .toList();
    }

    /**
     * Coloca unidades en una ubicación concreta y devuelve cuántas entraron.
     *
     * Es la operación que hace exacta la recuperación de un pedido fallido: las unidades vuelven
     * primero a la ubicación de la que se retiraron, que es donde el encargado las dejó. Si ya no
     * caben, quien llama las recoloca con el criterio general y la mercancía no se pierde.
     */
    public int storeAt(Product product, String locationId, int units) {
        if (units < 1 || !usedVolumeByLocation.containsKey(locationId)) {
            return 0;
        }
        int freeVolume = locationCapacity - usedVolumeByLocation.get(locationId);
        int quantity = Math.min(freeVolume / product.unitVolume(), units);
        if (quantity <= 0) {
            return 0;
        }
        usedVolumeByLocation.merge(locationId, quantity * product.unitVolume(), Integer::sum);
        unitsByLocationAndProduct.get(locationId).merge(product.id(), quantity, Integer::sum);
        return quantity;
    }

    /**
     * Prefiere las ubicaciones que ya guardan este producto para no fragmentar de más. No exige
     * contigüidad: cualquier ubicación con unidades del mismo producto es válida.
     */
    private int placeWhereProductAlreadyLives(Product product, Map<String, Integer> placement, int units) {
        int remaining = units;
        for (String locationId : sortedByFreeVolumeAscending()) {
            if (remaining == 0) {
                break;
            }
            if (unitsAt(locationId, product.id()) <= 0) {
                continue;
            }
            int freeUnits = (locationCapacity - usedVolumeByLocation.get(locationId)) / product.unitVolume();
            int quantity = Math.min(freeUnits, remaining);
            if (quantity <= 0) {
                continue;
            }
            placement.merge(locationId, quantity, Integer::sum);
            remaining -= quantity;
        }
        return units - remaining;
    }

    /** Mejor ajuste: la ubicación libre más pequeña que todavía admite una unidad completa. */
    private void placeInBestFitFreeLocations(Product product, Map<String, Integer> placement, int units) {
        int remaining = units;
        for (String locationId : sortedByFreeVolumeAscending()) {
            if (remaining == 0) {
                break;
            }
            if (placement.containsKey(locationId)) {
                continue;
            }
            int freeVolume = locationCapacity - usedVolumeByLocation.get(locationId);
            if (freeVolume < product.unitVolume()) {
                continue;
            }
            int quantity = Math.min(freeVolume / product.unitVolume(), remaining);
            placement.merge(locationId, quantity, Integer::sum);
            remaining -= quantity;
        }
    }

    /**
     * Orden estable por volumen libre ascendente y después por identificador. La reproducibilidad
     * importa: dos ejecuciones con la misma carga deben producir la misma distribución física.
     */
    private List<String> sortedByFreeVolumeAscending() {
        return capacityByLocation.keySet().stream()
                .sorted(Comparator.comparingInt((String id) -> locationCapacity - usedVolumeByLocation.get(id))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Libera el espacio de una ubicación al retirar unidades ya colocadas. */
    public void withdraw(String locationId, String productId, int units, int unitVolume) {
        Map<String, Integer> byProduct = unitsByLocationAndProduct.get(locationId);
        int present = byProduct.getOrDefault(productId, 0);
        if (units <= 0 || units > present) {
            throw new IllegalArgumentException("La ubicación no contiene la cantidad solicitada para retirar.");
        }
        int quantity = units;
        int left = present - quantity;
        usedVolumeByLocation.merge(locationId, -quantity * unitVolume, Integer::sum);
        // Una ubicación vacía no guarda rastro del producto: se borra la entrada en lugar de dejar
        // un cero, para que el detalle por ubicación nunca reporte unidades que no existen.
        if (left > 0) {
            byProduct.put(productId, left);
        } else {
            byProduct.remove(productId);
        }
    }

    /**
     * Vista por producto y ubicación del uso real del almacén.
     * Solo aparecen las ubicaciones que guardan unidades: un producto sin unidades no aparece.
     */
    public Map<String, Map<String, Integer>> unitsByProduct() {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        unitsByLocationAndProduct.forEach((locationId, byProduct) -> byProduct.forEach((productId, units) -> {
            if (units > 0) {
                result.computeIfAbsent(productId, key -> new LinkedHashMap<>()).put(locationId, units);
            }
        }));
        return result;
    }
}
