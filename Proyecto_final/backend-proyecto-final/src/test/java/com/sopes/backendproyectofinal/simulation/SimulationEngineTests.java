package com.sopes.backendproyectofinal.simulation;

import com.sopes.backendproyectofinal.catalog.CatalogService;
import com.sopes.backendproyectofinal.catalog.MerchandiseType;
import com.sopes.backendproyectofinal.inventory.InventoryService;
import com.sopes.backendproyectofinal.inventory.WarehouseSettings;
import com.sopes.backendproyectofinal.orders.*;
import com.sopes.backendproyectofinal.resources.ResourceManager;
import com.sopes.backendproyectofinal.shared.SimulationState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprueba el ciclo completo del trabajador: retira un pedido, reserva su mercancía, lo procesa con
 * recursos limitados y, si algo falla, devuelve la mercancía al inventario.
 */
class SimulationEngineTests {
    private OrderRepository repository;
    private InventoryService inventory;
    private ResourceManager resources;
    private StagePlanner planner;
    private SimulationEngine engine;
    private SimulationClock clock;

    /** Inventario con existencias de sobra, para que la prueba mida el motor y no la escasez. */
    private static InventoryService stockedInventory(Map<String, Integer> stock) {
        WarehouseSettings settings = new WarehouseSettings();
        settings.setAisles(25);
        settings.setInitialStock(stock);
        return new InventoryService(new CatalogService(), settings);
    }

    @BeforeEach
    void setUp() {
        SimulationState state = new SimulationState();
        SimulationSettings settings = new SimulationSettings();
        settings.setSimulatedSecondsPerRealSecond(600);
        inventory = stockedInventory(Map.of("GEN-01", 900, "FRA-01", 40, "PES-01", 40, "ELE-01", 40));
        repository = new OrderRepository(state, inventory);
        resources = new ResourceManager(state);
        planner = new StagePlanner(settings);
        clock = new SimulationClock(settings);
        engine = new SimulationEngine(repository, inventory, resources, planner, clock, settings, state);
    }

    @AfterEach
    void stop() { engine.close(); }

    /** Crea y registra un pedido de un solo producto del catálogo, con existencias de sobra. */
    private Order create(String productId, int quantity, MerchandiseType type) {
        Order order = new Order(UUID.randomUUID().toString(), "customer", "Cliente", 3,
                List.of(new OrderLine(productId, "Producto", type, quantity)), quantity,
                OrderStatus.CREATED, Instant.now());
        repository.save(order);
        return order;
    }

    @Test
    void completesMixedOrderAndReleasesResourcesWithProportionalWork() {
        Order small = create("FRA-01", 2, MerchandiseType.FRAGILE);
        Order large = create("FRA-01", 4, MerchandiseType.FRAGILE);
        assertEquals(2 * planner.plan(small).stream().mapToDouble(StagePlanner.Stage::simulatedSeconds).sum(),
                planner.plan(large).stream().mapToDouble(StagePlanner.Stage::simulatedSeconds).sum());
        Order mixed = new Order(UUID.randomUUID().toString(), "customer", "Mixto", 1,
                List.of(new OrderLine("FRA-01", "Frágil", MerchandiseType.FRAGILE, 2),
                        new OrderLine("PES-01", "Pesado", MerchandiseType.HEAVY, 2)), 4,
                OrderStatus.CREATED, Instant.now());
        repository.save(mixed);
        engine.start();
        engine.start();
        await().atMost(10, TimeUnit.SECONDS).until(() -> repository.findAll().stream()
                .allMatch(order -> order.status() == OrderStatus.COMPLETED));
        Order completed = repository.findById(mixed.id()).orElseThrow();
        assertEquals(100, completed.progress());
        assertEquals(OrderStage.COMPLETED, completed.stage());
        assertNotNull(completed.startedAt());
        assertNotNull(completed.completedAt());
        assertEquals(0, completed.estimatedRemainingMinutes());
        assertTrue(resources.snapshot().stream().allMatch(resource -> resource.inUse() == 0));
        // La mercancía salió del almacén: las existencias bajaron y no quedó nada comprometido.
        assertEquals(32, inventory.snapshot().item("FRA-01").stock());
        assertEquals(0, inventory.snapshot().totalReserved());
    }

    @Test
    void handlesContentionAndCancellationThenCanRestart() {
        for (int index = 0; index < 14; index++) create("GEN-01", 60, MerchandiseType.GENERAL);
        engine.start();
        await().atMost(3, TimeUnit.SECONDS).until(() -> resources.pendingRequests().size() >= 1);
        assertTrue(repository.findAll().stream().filter(order -> order.status() == OrderStatus.PROCESSING).count() > 1);
        resources.snapshot().forEach(resource -> {
            assertTrue(resource.inUse() <= resource.capacity());
            assertEquals(resource.inUse(), resource.instances().stream().filter(instance -> instance.ownerId() != null).count());
        });
        engine.stop();
        assertEquals(SimulationEngine.EngineStatus.STOPPED, engine.status());
        assertTrue(resources.pendingRequests().isEmpty());
        assertTrue(resources.snapshot().stream().allMatch(resource -> resource.inUse() == 0));
        assertTrue(repository.findAll().stream().allMatch(order -> order.status() == OrderStatus.CREATED
                || order.status() == OrderStatus.WAITING_STOCK || order.status() == OrderStatus.ERROR));
        assertTrue(repository.findAll().stream().filter(order -> order.status() == OrderStatus.ERROR)
                .allMatch(order -> order.errorMessage() != null));
        // Detener el motor no pierde mercancía: todo lo tomado o reservado vuelve al inventario.
        assertEquals(900, inventory.snapshot().item("GEN-01").stock());
        assertEquals(0, inventory.snapshot().totalReserved());
        // El reloj detenido permanece constante y los pedidos no seleccionados se conservan.
        double stoppedTime = clock.elapsedMinutes();
        assertEquals(stoppedTime, clock.elapsedMinutes());
        Order next = create("GEN-01", 1, MerchandiseType.GENERAL);
        engine.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> repository.findById(next.id()).orElseThrow().status() == OrderStatus.COMPLETED);
    }

    @Test
    void acceptsOrdersAfterWorkersAreAlreadyWaiting() {
        engine.start();
        Order order = create("ELE-01", 1, MerchandiseType.ELECTRONICS);
        await().atMost(3, TimeUnit.SECONDS).until(() -> repository.findById(order.id()).orElseThrow().status() == OrderStatus.COMPLETED);
        engine.stop();
        assertTrue(resources.snapshot().stream().allMatch(resource -> resource.inUse() == 0));
    }

    /** Un pedido sin existencias no ocupa a ningún trabajador ni bloquea a los que sí las tienen. */
    @Test
    void orderWithoutStockWaitsWithoutBlockingTheQueue() {
        Order blocked = new Order(UUID.randomUUID().toString(), "customer", "Sin stock", 1,
                List.of(new OrderLine("VOL-01", "Voluminoso", MerchandiseType.BULKY, 5)), 5,
                OrderStatus.CREATED, Instant.now());
        repository.save(blocked);
        Order served = create("GEN-01", 1, MerchandiseType.GENERAL);
        engine.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> repository.findById(served.id()).orElseThrow().status() == OrderStatus.COMPLETED);
        assertEquals(OrderStatus.WAITING_STOCK, repository.findById(blocked.id()).orElseThrow().status());
        assertEquals(0, inventory.snapshot().item("VOL-01").stock());
    }

    @Test
    void releasesLeaseAndReturnsStockWhenStageThrowsUnexpectedException() {
        SimulationState state = new SimulationState();
        SimulationSettings settings = new SimulationSettings();
        InventoryService failingInventory = stockedInventory(Map.of("GEN-01", 10));
        OrderRepository failingRepository = new OrderRepository(state, failingInventory);
        ResourceManager failingResources = new ResourceManager(state);
        SimulationClock failingClock = new SimulationClock(settings) {
            @Override public long realNanos(double simulatedSeconds) {
                throw new IllegalStateException("Falla controlada de la prueba.");
            }
        };
        SimulationEngine failingEngine = new SimulationEngine(failingRepository, failingInventory,
                failingResources, new StagePlanner(settings), failingClock, settings, state);
        Order order = new Order("failed-order", "customer", "Cliente", 3,
                List.of(new OrderLine("GEN-01", "Producto", MerchandiseType.GENERAL, 4)), 4,
                OrderStatus.CREATED, Instant.now());
        failingRepository.save(order);
        try {
            failingEngine.start();
            await().atMost(3, TimeUnit.SECONDS).until(() -> failingRepository.findById(order.id()).orElseThrow().status() == OrderStatus.ERROR);
            assertTrue(failingResources.snapshot().stream().allMatch(resource -> resource.inUse() == 0));
            // La mercancía tomada se devolvió al almacén con la misma cantidad.
            assertEquals(10, failingInventory.snapshot().item("GEN-01").stock());
            assertEquals(0, failingInventory.snapshot().totalReserved());
        } finally { failingEngine.close(); }
    }
}
