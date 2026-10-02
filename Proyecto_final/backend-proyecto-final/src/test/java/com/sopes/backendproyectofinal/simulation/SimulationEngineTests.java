package com.sopes.backendproyectofinal.simulation;

import com.sopes.backendproyectofinal.catalog.MerchandiseType;
import com.sopes.backendproyectofinal.orders.*;
import com.sopes.backendproyectofinal.resources.ResourceManager;
import com.sopes.backendproyectofinal.shared.SimulationState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class SimulationEngineTests {
    private OrderRepository repository;
    private ResourceManager resources;
    private StagePlanner planner;
    private SimulationEngine engine;
    private SimulationClock clock;

    @BeforeEach
    void setUp() {
        SimulationState state = new SimulationState();
        SimulationSettings settings = new SimulationSettings();
        settings.setSimulatedSecondsPerRealSecond(600);
        repository = new OrderRepository(state);
        resources = new ResourceManager(state);
        planner = new StagePlanner(settings);
        clock = new SimulationClock(settings);
        engine = new SimulationEngine(repository, resources, planner, clock, settings, state);
    }

    @AfterEach
    void stop() { engine.close(); }

    private Order create(int quantity, MerchandiseType type) {
        Order order = new Order(UUID.randomUUID().toString(), "customer", "Cliente", 3,
                List.of(new OrderLine("product", "Producto", type, quantity)), quantity, OrderStatus.CREATED, Instant.now());
        repository.save(order);
        return order;
    }

    @Test
    void completesMixedOrderAndReleasesResourcesWithProportionalWork() {
        Order small = create(2, MerchandiseType.FRAGILE);
        Order large = create(4, MerchandiseType.FRAGILE);
        assertEquals(2 * planner.plan(small).stream().mapToDouble(StagePlanner.Stage::simulatedSeconds).sum(),
                planner.plan(large).stream().mapToDouble(StagePlanner.Stage::simulatedSeconds).sum());
        Order mixed = new Order(UUID.randomUUID().toString(), "customer", "Mixto", 1,
                List.of(new OrderLine("fragile", "Frágil", MerchandiseType.FRAGILE, 2),
                        new OrderLine("heavy", "Pesado", MerchandiseType.HEAVY, 2)), 4, OrderStatus.CREATED, Instant.now());
        repository.save(mixed);
        engine.start();
        engine.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> repository.findAll().stream().allMatch(order -> order.status() == OrderStatus.COMPLETED));
        Order completed = repository.findById(mixed.id()).orElseThrow();
        assertEquals(100, completed.progress());
        assertEquals(OrderStage.COMPLETED, completed.stage());
        assertNotNull(completed.startedAt());
        assertNotNull(completed.completedAt());
        assertEquals(0, completed.estimatedRemainingMinutes());
        assertTrue(resources.snapshot().stream().allMatch(resource -> resource.inUse() == 0));
    }

    @Test
    void handlesContentionAndCancellationThenCanRestart() {
        for (int index = 0; index < 14; index++) create(100, MerchandiseType.GENERAL);
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
        assertTrue(repository.findAll().stream().allMatch(order -> order.status() == OrderStatus.CREATED || order.status() == OrderStatus.ERROR));
        assertTrue(repository.findAll().stream().filter(order -> order.status() == OrderStatus.ERROR).allMatch(order -> order.errorMessage() != null));
        // El reloj detenido permanece constante y los pedidos no seleccionados se conservan.
        double stoppedTime = clock.elapsedMinutes();
        assertEquals(stoppedTime, clock.elapsedMinutes());
        Order next = create(1, MerchandiseType.GENERAL);
        engine.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> repository.findById(next.id()).orElseThrow().status() == OrderStatus.COMPLETED);
    }

    @Test
    void acceptsOrdersAfterWorkersAreAlreadyWaiting() {
        engine.start();
        Order order = create(1, MerchandiseType.ELECTRONICS);
        await().atMost(3, TimeUnit.SECONDS).until(() -> repository.findById(order.id()).orElseThrow().status() == OrderStatus.COMPLETED);
        engine.stop();
        assertTrue(resources.snapshot().stream().allMatch(resource -> resource.inUse() == 0));
    }

    @Test
    void releasesLeaseWhenStageThrowsUnexpectedException() {
        SimulationState state = new SimulationState();
        SimulationSettings settings = new SimulationSettings();
        OrderRepository failingRepository = new OrderRepository(state);
        ResourceManager failingResources = new ResourceManager(state);
        SimulationClock failingClock = new SimulationClock(settings) {
            @Override public long realNanos(double simulatedSeconds) {
                throw new IllegalStateException("Falla controlada de la prueba.");
            }
        };
        SimulationEngine failingEngine = new SimulationEngine(failingRepository, failingResources,
                new StagePlanner(settings), failingClock, settings, state);
        Order order = new Order("failed-order", "customer", "Cliente", 3,
                List.of(new OrderLine("product", "Producto", MerchandiseType.GENERAL, 1)), 1, OrderStatus.CREATED, Instant.now());
        failingRepository.save(order);
        try {
            failingEngine.start();
            await().atMost(3, TimeUnit.SECONDS).until(() -> failingRepository.findById(order.id()).orElseThrow().status() == OrderStatus.ERROR);
            assertTrue(failingResources.snapshot().stream().allMatch(resource -> resource.inUse() == 0));
        } finally { failingEngine.close(); }
    }
}
