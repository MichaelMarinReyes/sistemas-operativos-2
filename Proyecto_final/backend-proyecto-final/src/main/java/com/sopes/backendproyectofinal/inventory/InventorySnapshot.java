package com.sopes.backendproyectofinal.inventory;

import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.warehouse.Lot;
import com.sopes.backendproyectofinal.warehouse.StorageLocation;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vista inmutable y coherente del inventario en un instante.
 *
 * El servicio de inventario publica una de estas después de cada modificación, en un campo volátil.
 * Quien solo necesita leer no toma ningún bloqueo: consume la instantánea publicada.
 *
 * El diseño resuelve un interbloqueo clásico. Para elegir pedidos elegibles, el planificador
 * necesita saber si hay existencias. Si tomara el bloqueo del inventario mientras sostiene el
 * bloqueo de pedidos, aparecería un orden de bloqueo en un sentido (pedidos, inventario) mientras
 * que el servicio de inventario necesita el de pedidos en el otro (inventario, pedidos): eso es un
 * interbloqueo por espera mutua en sentido contrario. Con la instantánea, la elegibilidad se resuelve
 * sin tomar bloqueos y nunca se anidan dos bloqueos.
 *
 * Que la instantánea pueda estar desactualizada no molesta: es una pista rápida para no despertar
 * trabajadores que no pueden avanzar. La comprobación que manda es la de la reserva, dentro del
 * bloqueo del inventario.
 */
public record InventorySnapshot(long revision, Instant generatedAt, Map<String, InventoryItem> items,
                                List<Lot> lots, List<StorageLocation> locations,
                                int occupiedVolume, int availableVolume,
                                Map<String, ReceiptPlacement> receipts) {

    public InventorySnapshot {
        items = Map.copyOf(items);
        lots = List.copyOf(lots);
        locations = List.copyOf(locations);
        receipts = Map.copyOf(receipts);
    }

    /** Existencias de un producto, o un elemento vacío si el producto no tiene unidades. */
    public InventoryItem item(String productId) {
        return items.getOrDefault(productId, new InventoryItem(productId, 0, 0, 0));
    }

    /** Unidades que un pedido nuevo podría reservar ahora mismo, sin reservar nada. */
    public int availableOf(String productId) {
        return item(productId).available();
    }

    /**
     * Indica si hay existencias suficientes para todas las líneas del pedido. No reserva nada: es una
     * consulta sin efectos. Sirve para que un pedido sin existencias no bloquee a toda la cola.
     */
    public boolean canSatisfy(Order order) {
        Map<String, Integer> required = new LinkedHashMap<>();
        order.lines().forEach(line -> required.merge(line.productId(), line.quantity(), Integer::sum));
        return required.entrySet().stream()
                .allMatch(entry -> availableOf(entry.getKey()) >= entry.getValue());
    }

    /** Producto que impide mover un pedido, para explicarle al usuario por qué espera. */
    public String blockingProduct(Order order) {
        return order.lines().stream()
                .filter(line -> availableOf(line.productId()) < line.quantity())
                .map(line -> line.productId()).findFirst().orElse(null);
    }

    /** Suma de existencias de todo el catálogo. */
    public int totalStock() {
        return items.values().stream().mapToInt(InventoryItem::stock).sum();
    }

    /** Suma de unidades comprometidas con pedidos en curso. */
    public int totalReserved() {
        return items.values().stream().mapToInt(InventoryItem::reserved).sum();
    }

    /** Unidades que esperan lugar en el almacén porque no había espacio. */
    public int totalPendingPlacement() {
        return items.values().stream().mapToInt(InventoryItem::pendingPlacement).sum();
    }
}
