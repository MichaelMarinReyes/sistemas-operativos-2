package com.sopes.backendproyectofinal.status;

import com.sopes.backendproyectofinal.orders.OrderService;
import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.resources.ResourceManager;
import com.sopes.backendproyectofinal.simulation.SimulationEngine;
import java.time.Instant;
import java.util.List;

/** Instantánea de consulta; las estadísticas de pedidos provienen del módulo de pedidos. */
public record StatusResponse(int contractVersion, String runId, long sequence, Instant generatedAt,
                             SimulationEngine.EngineStatus simulationStatus, String policy,
                             double elapsedMinutes, int timeScale, int workerCount,
                             List<ResourceManager.ResourceSummary> resources,
                             List<ResourceManager.ResourceRequest> resourceRequests,
                             WarehouseSummary warehouse, OrderService.Summary orders,
                             long ordersRevision, List<Order> activeOrders, List<Order> waitingOrders) {
    public record WarehouseSummary(int locations, int volumePerLocation, int occupiedVolume) {}
}
