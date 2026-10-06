package com.sopes.backendproyectofinal.status;

import com.sopes.backendproyectofinal.orders.OrderService;
import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.orders.OrderRepository;
import com.sopes.backendproyectofinal.orders.OrderStatus;
import com.sopes.backendproyectofinal.inventory.InventoryService;
import com.sopes.backendproyectofinal.inventory.InventorySnapshot;
import com.sopes.backendproyectofinal.receipts.ReceiptService;
import com.sopes.backendproyectofinal.resources.ResourceManager;
import com.sopes.backendproyectofinal.shared.SimulationState;
import com.sopes.backendproyectofinal.simulation.SimulationClock;
import com.sopes.backendproyectofinal.simulation.SimulationEngine;
import com.sopes.backendproyectofinal.simulation.SimulationSettings;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StatusService {
    private final OrderService orders;
    private final OrderRepository repository;
    private final ResourceManager resources;
    private final SimulationEngine engine;
    private final SimulationClock clock;
    private final SimulationSettings settings;
    private final SimulationState state;
    private final InventoryService inventory;
    private final ReceiptService receipts;
    private final String runId = UUID.randomUUID().toString();
    private long sequence;

    public StatusService(OrderService orders, OrderRepository repository, ResourceManager resources,
                         SimulationEngine engine, SimulationClock clock, SimulationSettings settings,
                         SimulationState state, InventoryService inventory, ReceiptService receipts) {
        this.orders = orders; this.repository = repository; this.resources = resources;
        this.engine = engine; this.clock = clock; this.settings = settings; this.state = state;
        this.inventory = inventory;
        this.receipts = receipts;
    }

    /**
     * Captura inmutable para el flujo de eventos; nunca envía red aquí.
     *
     * El resumen del almacén y el del inventario salen de la instantánea publicada del inventario, que
     * se lee sin tomar ningún bloqueo. Así el endpoint de eventos nunca compite con los trabajadores
     * por el bloqueo del almacén y una consulta lenta del navegador no puede frenar la simulación.
     *
     * Los pedidos sí se leen bajo el bloqueo de SimulationState, porque esa captura tiene que incluir
     * una asignación coherente de pedidos a trabajadores.
     */
    public StatusResponse getStatus() {
        InventorySnapshot snapshot = inventory.snapshot();
        state.lock.lock();
        try {
            List<Order> all = repository.findAll();
            List<Order> active = all.stream().filter(order -> order.status() == OrderStatus.QUEUED
                    || order.status() == OrderStatus.WAITING_RESOURCES || order.status() == OrderStatus.PROCESSING
                    || order.status() == OrderStatus.DEADLOCKED).toList();
            List<Order> waiting = all.stream()
                    .filter(order -> order.status() == OrderStatus.CREATED
                            || order.status() == OrderStatus.WAITING_STOCK)
                    .sorted(Comparator.comparingInt(Order::serviceLevel).thenComparing(Order::createdAt)
                            .thenComparing(Order::id))
                    .limit(50).toList();
            return new StatusResponse(5, runId, ++sequence, Instant.now(), engine.status(), "STRICT_PRIORITY",
                    clock.elapsedMinutes(), settings.getSimulatedSecondsPerRealSecond(), settings.getWorkerCount(),
                    resources.snapshot(), resources.pendingRequests(),
                    new StatusResponse.WarehouseSummary(snapshot.locations().size(),
                            snapshot.locations().isEmpty() ? 0 : snapshot.locations().get(0).capacity(),
                            snapshot.occupiedVolume(), snapshot.availableVolume()),
                    new StatusResponse.InventorySummary(snapshot.revision(), snapshot.totalStock(),
                            snapshot.totalReserved(), snapshot.totalPendingPlacement(),
                             snapshot.totalStock() - snapshot.totalReserved() - snapshot.totalPendingPlacement()),
                    orders.summarize(), repository.revision(), active, waiting, receipts.report());
        } finally { state.lock.unlock(); }
    }
}
