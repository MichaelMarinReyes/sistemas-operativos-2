package com.sopes.backendproyectofinal.inventory;

import com.sopes.backendproyectofinal.catalog.CatalogService;
import com.sopes.backendproyectofinal.catalog.MerchandiseType;
import com.sopes.backendproyectofinal.orders.*;
import com.sopes.backendproyectofinal.shared.SimulationState;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Comprueba cantidades físicas, compromisos y publicaciones, además de los contadores globales. */
class InventoryIntegrityTests {
    private InventoryService inventory(int units, int locations) {
        var settings = new WarehouseSettings();
        settings.setAisles(1);
        settings.setShelvesPerAisle(locations);
        settings.setLevelsPerShelf(1);
        settings.setInitialStock(Map.of("GEN-01", units));
        return new InventoryService(new CatalogService(), settings);
    }

    private Order order(int units) {
        return new Order(UUID.randomUUID().toString(), "customer", "Cliente", 3,
                List.of(new OrderLine("GEN-01", "Cuaderno", MerchandiseType.GENERAL, units)),
                units, OrderStatus.CREATED, Instant.now());
    }

    private void assertConserved(InventoryService inventory) {
        var snapshot = inventory.snapshot();
        snapshot.items().forEach((id, item) -> {
            int stored = snapshot.lots().stream().filter(lot -> lot.productId().equals(id))
                    .mapToInt(lot -> lot.units()).sum();
            assertThat(item.stock()).isEqualTo(stored + item.pendingPlacement());
            assertThat(item.reserved()).isBetween(0, stored);
            assertThat(item.available()).isEqualTo(stored - item.reserved());
        });
        assertThat(snapshot.lots().stream().mapToInt(lot -> lot.usedVolume()).sum())
                .isEqualTo(snapshot.occupiedVolume());
    }

    @Test
    void outstandingReservationsNeverSharePhysicalUnits() {
        var inventory = inventory(20, 2);
        var first = inventory.reserve(order(10)).orElseThrow();
        var second = inventory.reserve(order(10)).orElseThrow();
        assertThat(first.lines().get(0).locationId()).isNotEqualTo(second.lines().get(0).locationId());
        assertThat(inventory.consume(second).totalUnits()).isEqualTo(10);
        assertConserved(inventory);
        assertThat(inventory.consume(first).totalUnits()).isEqualTo(10);
        assertConserved(inventory);
        assertThat(inventory.snapshot().totalStock()).isZero();
        assertThat(inventory.snapshot().occupiedVolume()).isZero();
        inventory.findIssue(first.orderId()).ifPresent(inventory::recover);
        inventory.findIssue(second.orderId()).ifPresent(inventory::recover);
        assertConserved(inventory);
        assertThat(inventory.snapshot().totalStock()).isEqualTo(20);
    }

    @Test
    void overflowCannotCreateAnIncompleteReservation() {
        var inventory = inventory(15, 1);
        assertThat(inventory.snapshot().availableOf("GEN-01")).isEqualTo(10);
        assertThat(inventory.reserve(order(15))).isEmpty();
        assertThat(inventory.snapshot().totalReserved()).isZero();
        var first = inventory.reserve(order(10)).orElseThrow();
        assertThat(inventory.consume(first).totalUnits()).isEqualTo(10);
        assertConserved(inventory);
        assertThat(inventory.snapshot().totalPendingPlacement()).isZero();
        var second = inventory.reserve(order(5)).orElseThrow();
        assertThat(inventory.consume(second).totalUnits()).isEqualTo(5);
        assertConserved(inventory);
        assertThat(inventory.snapshot().totalReserved()).isZero();
        assertThat(inventory.snapshot().totalStock()).isZero();
    }

    @Test
    void concurrentReservationsAreConsumedWithoutPhantomStock() throws Exception {
        var inventory = inventory(100, 10);
        var pool = Executors.newFixedThreadPool(20);
        var start = new CountDownLatch(1);
        try {
            var reservations = new ArrayList<Future<StockReservation>>();
            for (int index = 0; index < 20; index++) {
                reservations.add(pool.submit(() -> {
                    start.await();
                    return inventory.reserve(order(5)).orElseThrow();
                }));
            }
            start.countDown();
            var granted = new ArrayList<StockReservation>();
            for (var future : reservations) granted.add(future.get(5, TimeUnit.SECONDS));
            var committed = new HashMap<String, Integer>();
            granted.forEach(reservation -> reservation.lines().forEach(line ->
                    committed.merge(line.locationId(), line.units(), Integer::sum)));
            assertThat(committed.values()).allMatch(units -> units <= 10);
            var issues = new ArrayList<Future<StockIssue>>();
            for (var reservation : granted) issues.add(pool.submit(() -> inventory.consume(reservation)));
            int withdrawn = 0;
            for (var future : issues) withdrawn += future.get(5, TimeUnit.SECONDS).totalUnits();
            assertThat(withdrawn).isEqualTo(100);
            assertConserved(inventory);
            assertThat(inventory.snapshot().totalStock()).isZero();
            assertThat(inventory.snapshot().totalReserved()).isZero();
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void recoveryPreservesStockWhenItsOriginIsAlreadyFull() {
        var inventory = inventory(10, 1);
        var reservation = inventory.reserve(order(10)).orElseThrow();
        var issue = inventory.consume(reservation);
        inventory.receive("GEN-01", 10);
        inventory.recover(issue);
        inventory.recover(issue);
        assertConserved(inventory);
        assertThat(inventory.snapshot().totalStock()).isEqualTo(20);
        assertThat(inventory.snapshot().totalPendingPlacement()).isEqualTo(10);
        assertThat(inventory.snapshot().availableOf("GEN-01")).isEqualTo(10);
    }

    @Test
    void waitingOrderWakesAfterStockArrives() throws Exception {
        var inventory = inventory(1, 2);
        var repository = new OrderRepository(new SimulationState(), inventory);
        var blocked = order(2);
        repository.save(blocked);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var selected = pool.submit(repository::awaitNextEligible);
            org.awaitility.Awaitility.await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(repository.findById(blocked.id()).orElseThrow().status())
                            .isEqualTo(OrderStatus.WAITING_STOCK));
            assertThat(selected.isDone()).isFalse();
            inventory.receive("GEN-01", 1);
            assertThat(selected.get(3, TimeUnit.SECONDS).id()).isEqualTo(blocked.id());
            var reservation = inventory.reserve(blocked).orElseThrow();
            inventory.release(reservation);
            inventory.release(reservation);
            assertConserved(inventory);
            assertThat(inventory.snapshot().totalReserved()).isZero();
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void eachHttpResponseUsesOneSnapshotEvenWhenStockChanges() {
        var service = inventory(10, 2);
        var before = service.snapshot();
        service.receive("GEN-01", 5);
        var after = service.snapshot();
        var inventory = spy(service);
        doReturn(before, after).when(inventory).snapshot();
        var controller = new InventoryController(inventory);
        var response = controller.inventory();
        assertThat(response.inventoryRevision()).isEqualTo(before.revision());
        assertThat(response.items().stream().mapToInt(item -> item.stock()).sum()).isEqualTo(response.totals().stock());
        verify(inventory, times(1)).snapshot();
        clearInvocations(inventory);
        doReturn(before, after).when(inventory).snapshot();
        var warehouse = controller.warehouse();
        assertThat(warehouse.inventoryRevision()).isEqualTo(before.revision());
        assertThat(warehouse.warehouse().occupiedVolume()).isEqualTo(before.occupiedVolume());
        verify(inventory, times(1)).snapshot();
    }
}
