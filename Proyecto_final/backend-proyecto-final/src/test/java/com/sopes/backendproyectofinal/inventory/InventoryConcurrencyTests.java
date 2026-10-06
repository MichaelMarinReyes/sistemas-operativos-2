package com.sopes.backendproyectofinal.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.sopes.backendproyectofinal.catalog.CatalogService;
import com.sopes.backendproyectofinal.catalog.MerchandiseType;
import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.orders.OrderLine;
import com.sopes.backendproyectofinal.orders.OrderRepository;
import com.sopes.backendproyectofinal.orders.OrderStatus;
import com.sopes.backendproyectofinal.shared.SimulationState;
import com.sopes.backendproyectofinal.warehouse.Lot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Demuestra, con pruebas, los cuatro problemas de concurrencia del día cuatro y cómo queda resuelto
 * cada uno. Cada prueba tiene el nombre del problema que ataca y comprueba el invariante que lo
 * impide.
 */
class InventoryConcurrencyTests {

    /** Inventario con la carga inicial indicada y el almacén de tamaño indicado. */
    private static InventoryService inventory(Map<String, Integer> stock, int aisles,
                                             int shelves, int levels) {
        WarehouseSettings settings = new WarehouseSettings();
        settings.setAisles(aisles);
        settings.setShelvesPerAisle(shelves);
        settings.setLevelsPerShelf(levels);
        settings.setInitialStock(stock);
        return new InventoryService(new CatalogService(), settings);
    }

    /** Inventario estándar de 100 ubicaciones con la carga indicada. */
    private static InventoryService inventory(Map<String, Integer> stock) {
        return inventory(stock, 10, 5, 2);
    }

    /** Pedido de un solo producto, listo para reservarse. */
    private static Order orderFor(String productId, int quantity) {
        return new Order(UUID.randomUUID().toString(), "customer", "Cliente", 3,
                List.of(new OrderLine(productId, "Producto", MerchandiseType.GENERAL, quantity)),
                quantity, OrderStatus.CREATED, Instant.now());
    }

    /** Ejecuta la acción en varias hebras que arrancan a la vez y devuelve los resultados. */
    private static <T> List<T> runConcurrently(int threads,
                                               java.util.function.IntFunction<T> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<T> results = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                final int id = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    results.add(action.apply(id));
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        return new ArrayList<>(results);
    }

    /**
     * Sobreventa. Dieciséis hebras piden a la vez las mismas unidades.
     *
     * Sin la comprobación y la escritura dentro de un mismo bloqueo, varias pasarían la
     * comprobación con las mismas unidades libres y el almacén prometería más de lo que tiene.
     * El invariante que se verifica es que lo comprometido nunca pasa de lo existente y que
     * exactamente las reservas que caben son las que se conceden.
     */
    @Test
    void neverSellsMoreUnitsThanItHasUnderConcurrentReservations() throws Exception {
        InventoryService inventory = inventory(Map.of("GEN-01", 50));
        int threads = 16;
        int perOrder = 5;

        List<Boolean> results = runConcurrently(threads,
                index -> inventory.reserve(orderFor("GEN-01", perOrder)).isPresent());

        InventoryItem item = inventory.snapshot().item("GEN-01");
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(10);
        assertThat(item.stock()).isEqualTo(50);
        assertThat(item.reserved()).isEqualTo(50);
        assertThat(item.available()).isZero();
        assertThat(item.isConsistent()).isTrue();
    }

    /**
     * Reserva parcial. Un pedido con una línea abundante y otra imposible.
     *
     * Si el inventario reservara línea por línea, la línea posible quedaría bloqueada para siempre
     * sin que el pedido pudiera completarse. El invariante es que una reserva existe entera o no
     * existe: al fallar, no queda ninguna unidad comprometida de ninguna línea.
     */
    @Test
    void leavesNothingReservedWhenOneLineIsMissing() {
        InventoryService inventory = inventory(Map.of("GEN-01", 100));
        Order mixed = new Order(UUID.randomUUID().toString(), "customer", "Mixto", 2,
                List.of(new OrderLine("GEN-01", "Abundante", MerchandiseType.GENERAL, 10),
                        new OrderLine("VOL-01", "Imposible", MerchandiseType.BULKY, 1)),
                11, OrderStatus.CREATED, Instant.now());

        assertThat(inventory.reserve(mixed)).isEmpty();

        InventoryItem item = inventory.snapshot().item("GEN-01");
        assertThat(item.reserved()).isZero();
        assertThat(item.available()).isEqualTo(100);
        assertThat(inventory.snapshot().item("VOL-01").stock()).isZero();
    }

    /**
     * Reserva repetida del mismo pedido. Un worker reintentando no debe prometer la misma
     * mercancía dos veces: la reserva es idempotente.
     */
    @Test
    void reservationIsIdempotentForTheSameOrder() {
        InventoryService inventory = inventory(Map.of("GEN-01", 20));
        Order order = orderFor("GEN-01", 6);

        Optional<StockReservation> first = inventory.reserve(order);
        Optional<StockReservation> second = inventory.reserve(order);

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(second.get().lines()).isEqualTo(first.get().lines());
        assertThat(inventory.snapshot().item("GEN-01").reserved()).isEqualTo(6);
    }

    /**
     * Asignación no contigua. Un lote no vive en un tramo seguido de estantes.
     *
     * La escena se construye a propósito: se llena el almacén con mercancía grande que deja huecos
     * inutilizables, de forma que cuando llega el último producto solo caben sus unidades en
     * posiciones separadas. Se comprueba de manera objetiva: se convierte cada identificador de
     * ubicación en su posición física dentro de la estantería y se exige que el espacio que abarca el
     * lote sea mayor que la cantidad de ubicaciones que usa. Si el lote fuera contiguo, ambos números
     * serían iguales.
     */
    @Test
    void allocatesLotsAcrossNonContiguousLocations() {
        InventoryService inventory = inventory(Map.of(), 1, 5, 1);
        inventory.receive("FRA-02", 8);
        inventory.receive("VOL-01", 1);
        inventory.receive("GEN-02", 3);
        inventory.receive("ELE-02", 2);

        InventoryService.LotReport lot = inventory.warehouseReport().lots().stream()
                .filter(candidate -> candidate.productId().equals("ELE-02")).findFirst().orElseThrow();

        assertThat(lot.units()).isEqualTo(2);
        assertThat(lot.split()).isTrue();
        assertThat(lot.allocations()).hasSize(2);
        List<Integer> positions = lot.allocations().stream()
                .map(allocation -> position(allocation.locationId(), 5, 1)).sorted().toList();
        assertThat(positions.get(positions.size() - 1) - positions.get(0) + 1)
                .isGreaterThan(positions.size());
    }

    /** Posición física de una ubicación dentro de la estantería, contando desde la primera. */
    private static int position(String locationId, int shelvesPerAisle, int levelsPerShelf) {
        String[] parts = locationId.split("-");
        int aisle = parts[0].charAt(0) - 'A';
        int shelf = Integer.parseInt(parts[1]) - 1;
        int level = Integer.parseInt(parts[2]) - 1;
        return aisle * shelvesPerAisle * levelsPerShelf + shelf * levelsPerShelf + level;
    }

    /**
     * Trazabilidad y recuperación. Un pedido que falla después de tomar su carga devuelve las
     * unidades a las mismas ubicaciones de las que salieron.
     *
     * Sin registro del retiro no habría forma de saber qué unidades ni de dónde salir, y el
     * inventario quedaría descuadrado de forma irreversible. El invariante es que existencias más
     * unidades retiradas se conserva, y que la recuperación es exacta e idempotente.
     */
    @Test
    void recoversWithdrawnStockToItsOriginAndOnlyOnce() {
        InventoryService inventory = inventory(Map.of("GEN-01", 30));
        Map<String, Integer> before = distribution(inventory, "GEN-01");
        Order order = orderFor("GEN-01", 12);

        StockReservation reservation = inventory.reserve(order).orElseThrow();
        StockIssue issue = inventory.consume(reservation);

        assertThat(issue.lines()).isNotEmpty();
        assertThat(issue.totalUnits()).isEqualTo(12);
        assertThat(inventory.snapshot().item("GEN-01").stock()).isEqualTo(18);
        assertThat(distribution(inventory, "GEN-01")).isNotEqualTo(before);

        inventory.recover(issue);
        inventory.recover(issue);

        InventoryItem item = inventory.snapshot().item("GEN-01");
        assertThat(item.stock()).isEqualTo(30);
        assertThat(item.reserved()).isZero();
        assertThat(distribution(inventory, "GEN-01")).isEqualTo(before);
    }

    /** Distribución física de un producto: cuántas unidades hay en cada ubicación. */
    private static Map<String, Integer> distribution(InventoryService inventory, String productId) {
        return inventory.warehouseReport().lots().stream()
                .filter(lot -> lot.productId().equals(productId))
                .flatMap(lot -> lot.allocations().stream())
                .collect(java.util.stream.Collectors.toMap(allocation -> allocation.locationId(),
                        allocation -> allocation.units(), Integer::sum, java.util.LinkedHashMap::new));
    }

    /**
     * Lecturas concurrentes durante una sucesión de escrituras.
     *
     * La instantánea publicada se escribe en un campo volátil y se lee sin tomar bloqueos. Esta
     * prueba comprueba progreso de los lectores mientras un hilo modifica el inventario.
     * La ausencia de adquisición del bloqueo se verifica además en el diseño de snapshot.
     */
    @Test
    void snapshotIsReadableWithoutWaitingForWriters() throws Exception {
        InventoryService inventory = inventory(Map.of("GEN-01", 5000));
        AtomicBoolean writing = new AtomicBoolean(true);
        Thread writer = new Thread(() -> {
            while (writing.get()) {
                inventory.receive("GEN-01", 1);
            }
        });
        writer.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            int reads = 0;
            while (System.nanoTime() < deadline) {
                assertThat(inventory.snapshot().availableOf("GEN-01")).isPositive();
                reads++;
            }
            assertThat(reads).isGreaterThan(1000);
        } finally {
            writing.set(false);
            writer.join(5000);
        }
    }

    /**
     * Interbloqueo por orden de bloqueo. Elegibilidad y reserva al mismo tiempo.
     *
     * Elegir un pedido exige saber si hay existencias, y reservar exige escribir. Si se tomaran los
     * dos bloqueos a la vez, un hilo los pediría en el orden pedidos-inventario y el servicio de
     * inventario lo haría al revés, y ambos se quedarían esperando para siempre. Aquí la
     * elegibilidad se resuelve contra la instantánea publicada y la reserva toma su propio bloqueo
     * por separado. La prueba verifica progreso y cierre; el argumento general está en el análisis.
     */
    @Test
    void eligibilityAndReservationNeverBlockEachOther() throws Exception {
        SimulationState state = new SimulationState();
        InventoryService inventory = inventory(Map.of("GEN-01", 40));
        OrderRepository repository = new OrderRepository(state, inventory);
        for (int index = 0; index < 12; index++) {
            repository.save(orderFor("GEN-01", 3));
        }
        AtomicBoolean stop = new AtomicBoolean();
        Thread reserver = new Thread(() -> {
            while (!stop.get()) {
                Order candidate = orderFor("GEN-01", 3);
                inventory.reserve(candidate).ifPresent(inventory::consume);
            }
        });
        Thread selector = new Thread(() -> {
            while (!stop.get()) {
                try {
                    Order order = repository.awaitNextEligible();
                    Optional<StockReservation> reservation = inventory.reserve(order);
                    if (reservation.isEmpty()) {
                        repository.returnForStock(order.id());
                    } else {
                        inventory.consume(reservation.get());
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        selector.setDaemon(true);
        reserver.setDaemon(true);
        var failures = new ConcurrentLinkedQueue<Throwable>();
        selector.setUncaughtExceptionHandler((thread, failure) -> failures.add(failure));
        reserver.setUncaughtExceptionHandler((thread, failure) -> failures.add(failure));
        selector.start();
        reserver.start();
        try {
            Thread.sleep(2000);
        } finally {
            stop.set(true);
            selector.interrupt();
            reserver.join(5000);
            selector.join(5000);
        }
        assertThat(failures).isEmpty();
        assertThat(reserver.isAlive()).isFalse();
        assertThat(selector.isAlive()).isFalse();
        InventoryItem item = inventory.snapshot().item("GEN-01");
        assertThat(item.available()).isNotNegative();
        assertThat(item.reserved()).isZero();
        assertThat(item.stock() + item.pendingPlacement()).isPositive();
    }

    /**
     * Una reserva perdida devuelve el pedido a la espera en lugar de dejarlo en cola para siempre.
     * La reserva es la comprobación que manda; la instantánea solo evita despertar trabajo inútil.
     */
    @Test
    void staleSnapshotLosesCleanlyAndTheOrderReturnsToTheQueue() {
        InventoryService inventory = inventory(Map.of("GEN-01", 5));
        Order first = orderFor("GEN-01", 5);
        Order second = orderFor("GEN-01", 5);

        assertThat(inventory.snapshot().canSatisfy(second)).isTrue();
        assertThat(inventory.reserve(first)).isPresent();
        assertThat(inventory.snapshot().canSatisfy(second)).isFalse();

        assertThat(inventory.reserve(second)).isEmpty();
        assertThat(inventory.snapshot().item("GEN-01").reserved()).isEqualTo(5);
        assertThat(inventory.snapshot().blockingProduct(second)).isEqualTo("GEN-01");
    }

    /**
     * Mercancía sin lugar. Lo que no cabe en el almacén no se pierde: queda pendiente y se
     * coloca en cuanto un pedido libera espacio. El invariante es que ninguna unidad se pierde ni
     * se cuenta dos veces.
     */
    @Test
    void keepsOverflowAsPendingPlacementAndPlacesItWhenSpaceIsFreed() {
        InventoryService inventory = inventory(Map.of("GEN-01", 4), 1, 1, 1);

        assertThat(inventory.receive("GEN-01", 6).pendingUnits()).isZero();
        InventoryService.ReceiptResult overflow = inventory.receive("GEN-01", 5);

        assertThat(overflow.placedUnits()).isZero();
        assertThat(overflow.pendingUnits()).isEqualTo(5);
        assertThat(inventory.snapshot().item("GEN-01").pendingPlacement()).isEqualTo(5);
        assertThat(inventory.snapshot().availableVolume()).isZero();

        StockReservation reservation = inventory.reserve(orderFor("GEN-01", 4)).orElseThrow();
        inventory.consume(reservation);

        InventoryItem item = inventory.snapshot().item("GEN-01");
        assertThat(item.stock()).isEqualTo(11);
        assertThat(item.pendingPlacement()).isEqualTo(1);
        assertThat(item.isConsistent()).isTrue();
    }

    /**
     * Aviso de disponibilidad. Cada cambio en el inventario despierta a quien espera pedidos, para
     * que un pedido bloqueado por falta de mercancía avance en cuanto haya espacio o reposición.
     */
    @Test
    void notifiesListenersAfterEveryChange() {
        InventoryService inventory = inventory(Map.of("GEN-01", 1));
        int[] notifications = {0};
        inventory.addListener(() -> notifications[0]++);

        inventory.reserve(orderFor("GEN-01", 1));
        inventory.receive("GEN-01", 3);

        assertThat(notifications[0]).isEqualTo(2);
    }
}
