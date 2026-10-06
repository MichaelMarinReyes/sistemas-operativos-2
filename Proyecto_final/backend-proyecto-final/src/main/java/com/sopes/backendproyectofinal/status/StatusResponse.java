package com.sopes.backendproyectofinal.status;

import com.sopes.backendproyectofinal.orders.OrderService;
import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.resources.ResourceManager;
import com.sopes.backendproyectofinal.simulation.SimulationEngine;
import com.sopes.backendproyectofinal.receipts.ReceiptService;
import java.time.Instant;
import java.util.List;

/**
 * Instantánea de consulta que viaja por el flujo de eventos.
 *
 * Las estadísticas de pedidos provienen del módulo de pedidos y el almacén de la instantánea
 * publicada del inventario, de modo que este registro no necesita copiar ningún dato mutable.
 */
public record StatusResponse(int contractVersion, String runId, long sequence, Instant generatedAt,
                             SimulationEngine.EngineStatus simulationStatus, String policy,
                             double elapsedMinutes, int timeScale, int workerCount,
                             List<ResourceManager.ResourceSummary> resources,
                             List<ResourceManager.ResourceRequest> resourceRequests,
                             WarehouseSummary warehouse, InventorySummary inventory,
                             OrderService.Summary orders,
                             long ordersRevision, List<Order> activeOrders, List<Order> waitingOrders,
                             ReceiptService.ReceiptReport receipts) {
    public record WarehouseSummary(int locations, int volumePerLocation, int occupiedVolume,
                                   int availableVolume) {}

    /**
     * Resumen de existencias en el instante de la captura.
     * La revisión permite al navegador detectar que el inventario cambió entre eventos.
     */
    public record InventorySummary(long inventoryRevision, int stock, int reserved,
                                   int pendingPlacement, int available) {}
}
