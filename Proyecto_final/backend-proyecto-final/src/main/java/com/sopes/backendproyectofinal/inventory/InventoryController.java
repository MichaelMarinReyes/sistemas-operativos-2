package com.sopes.backendproyectofinal.inventory;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de existencias y del almacén.
 *
 * Solo lee. Ninguna ruta modifica inventario: las únicas entradas son el motor de pedidos y, a
 * partir del quinto día, el área de recepción. Mantener el inventario fuera del controlador es lo
 * que permite que esas rutas no rompan el aislamiento del módulo.
 */
@RestController
@RequestMapping("/api")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) { this.inventory = inventory; }

    /** Existencias por producto con su detalle de comprometido, disponible y pendiente. */
    @GetMapping("/inventory")
    public InventoryResponse inventory() {
        InventorySnapshot snapshot = inventory.snapshot();
        return new InventoryResponse(snapshot.revision(), snapshot.generatedAt(),
                inventory.report(snapshot), totals(snapshot));
    }

    /** Capacidad, ocupación y reparto real de los lotes entre ubicaciones no contiguas. */
    @GetMapping("/warehouse")
    public WarehouseResponse warehouse() {
        InventorySnapshot snapshot = inventory.snapshot();
        return new WarehouseResponse(snapshot.revision(), snapshot.generatedAt(), inventory.warehouseReport(snapshot));
    }

    /** Totales del inventario, para las tarjetas de resumen. */
    private Totals totals(InventorySnapshot snapshot) {
        return new Totals(snapshot.totalStock(), snapshot.totalReserved(),
                snapshot.totalStock() - snapshot.totalReserved() - snapshot.totalPendingPlacement(), snapshot.totalPendingPlacement(),
                snapshot.occupiedVolume(), snapshot.availableVolume());
    }

    public record InventoryResponse(long inventoryRevision, Instant generatedAt,
                                    List<InventoryService.InventoryReport> items, Totals totals) {}

    public record WarehouseResponse(long inventoryRevision, Instant generatedAt,
                                    InventoryService.WarehouseReport warehouse) {}

    /** Existencias, comprometido, libre y pendiente, más el uso de volumen del almacén. */
    public record Totals(int stock, int reserved, int available, int pendingPlacement,
                         int occupiedVolume, int availableVolume) {}
}
