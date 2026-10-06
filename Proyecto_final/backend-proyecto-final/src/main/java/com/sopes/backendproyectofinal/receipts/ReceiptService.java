package com.sopes.backendproyectofinal.receipts;

import com.sopes.backendproyectofinal.catalog.CatalogService;
import com.sopes.backendproyectofinal.inventory.*;
import com.sopes.backendproyectofinal.resources.*;
import com.sopes.backendproyectofinal.shared.ApiException;
import com.sopes.backendproyectofinal.simulation.SimulationClock;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Dos trabajadores y dos colas acotadas separan la recepción física del alta de existencias.
 * Recepción solo produce mensajes. El consumidor llama al propietario del inventario.
 * Nunca se retiene el escáner al esperar espacio en la cola o en el almacén.
 */
@Service
public class ReceiptService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptService.class);
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final ResourceManager resources;
    private final SimulationClock clock;
    private final ReceiptSettings settings;
    private final BlockingQueue<StockReceipt> incoming;
    private final BlockingQueue<StockReceipt> scanned;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Map<String, ReceiptView> entries = new LinkedHashMap<>();
    private volatile List<ReceiptView> published = List.of();
    private volatile boolean running;
    private ExecutorService workers;
    // Solo cada trabajador modifica su mensaje actual. El cierre espera su terminación antes de reiniciar.
    private StockReceipt producerCurrent;
    private StockReceipt consumerCurrent;

    public ReceiptService(CatalogService catalog, InventoryService inventory, ResourceManager resources,
                          SimulationClock clock, ReceiptSettings settings) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.resources = resources;
        this.clock = clock;
        this.settings = settings;
        incoming = new ArrayBlockingQueue<>(settings.getQueueCapacity(), true);
        scanned = new ArrayBlockingQueue<>(settings.getQueueCapacity(), true);
    }

    /**
     * La admisión espera como máximo el tiempo configurado; jamás espera espacio físico.
     * La clave identifica la operación aun cuando el cliente no recibió la confirmación HTTP.
     */
    public ReceiptView submit(String key, String productId, int quantity) {
        var product = catalog.findById(productId);
        if (quantity < 1 || quantity > 1000000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La cantidad debe estar entre uno y un millón.");
        }
        String id = "RCV-" + key;
        lock.lock();
        try {
            ReceiptView existing = entries.get(id);
            if (existing != null) {
                if (!existing.productId().equals(productId) || existing.quantity() != quantity) {
                    throw new ApiException(HttpStatus.CONFLICT, "La clave de recepción ya se usó con otros datos.");
                }
                return enrich(existing, inventory.snapshot());
            }
            long outstanding = published.stream().map(entry -> enrich(entry, inventory.snapshot()))
                    .filter(entry -> entry.status() != ReceiptStatus.COMPLETED && entry.status() != ReceiptStatus.ERROR).count();
            if (outstanding >= settings.getMaxOutstanding()) throw saturated();
            var message = new StockReceipt(id, productId, quantity);
            var receipt = new ReceiptView(id, productId, product.name(), quantity, ReceiptStatus.PENDING,
                    0, 0, Instant.now(), null, null);
            // El consumidor toma de la cola sin este bloqueo. Una oferta llena puede liberarse
            // mientras la admisión conserva la exclusión necesaria para evitar claves duplicadas.
            boolean accepted;
            try {
                accepted = incoming.offer(message, settings.getOfferTimeoutMilliseconds(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Se interrumpió la admisión. Reintenta con la misma clave.");
            }
            if (!accepted) throw saturated();
            entries.put(id, receipt);
            publish();
            return receipt;
        } finally { lock.unlock(); }
    }

    private ApiException saturated() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Recepción está saturada. El ingreso no fue aceptado; reintenta con la misma clave.");
    }

    public synchronized void start() {
        if (running) return;
        if (workers != null && !workers.isTerminated()) {
            throw new ApiException(HttpStatus.CONFLICT, "Recepción todavía se está deteniendo.");
        }
        running = true;
        workers = Executors.newFixedThreadPool(2);
        workers.submit(this::produce);
        workers.submit(this::consume);
    }

    /** Los mensajes en curso se conservan como puntos de reanudación, además de los encolados. */
    public synchronized void stop() {
        running = false;
        if (workers == null) return;
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.CONFLICT, "Recepción sigue liberando recursos; intenta detenerla nuevamente.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.CONFLICT, "Se interrumpió el cierre de recepción.");
        }
    }

    private void produce() {
        Thread.currentThread().setName("redxela-reception");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (producerCurrent == null) producerCurrent = incoming.take();
                StockReceipt message = producerCurrent;
                if (find(message.id()).scannedAt() == null) {
                    change(message.id(), entry -> entry.withStatus(ReceiptStatus.WAITING_SCANNER, null));
                    try (var lease = resources.acquire(message.id(), Map.of(ResourceType.SCANNER, 1, ResourceType.WORKER, 1))) {
                        change(message.id(), entry -> entry.withStatus(ReceiptStatus.SCANNING, null));
                        TimeUnit.NANOSECONDS.sleep(clock.realNanos((double) settings.getScanSecondsPerUnit() * message.units()));
                    }
                    change(message.id(), entry -> new ReceiptView(entry.id(), entry.productId(), entry.productName(),
                            entry.quantity(), ReceiptStatus.WAITING_INVENTORY, 0, 0, entry.createdAt(), Instant.now(), null));
                }
                while (!scanned.offer(message, settings.getOfferTimeoutMilliseconds(), TimeUnit.MILLISECONDS)) {
                    if (!running) throw new InterruptedException();
                }
                producerCurrent = null;
            } catch (InterruptedException exception) {
                if (producerCurrent != null) change(producerCurrent.id(), entry -> entry.withStatus(
                        entry.scannedAt() == null ? ReceiptStatus.PENDING : ReceiptStatus.WAITING_INVENTORY, null));
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                fail(producerCurrent, exception);
                producerCurrent = null;
            }
        }
    }

    private void consume() {
        Thread.currentThread().setName("redxela-inventory-consumer");
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (consumerCurrent == null) consumerCurrent = scanned.take();
                inventory.applyReceipt(consumerCurrent);
                // La confirmación vive en la instantánea del inventario, junto con sus cantidades.
                consumerCurrent = null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                fail(consumerCurrent, exception);
                consumerCurrent = null;
            }
        }
    }

    private void fail(StockReceipt message, Exception exception) {
        LOGGER.error("Falló una recepción; se conserva su registro para revisión", exception);
        if (message != null) change(message.id(), entry -> entry.withStatus(ReceiptStatus.ERROR,
                "No se pudo procesar la recepción. Revisa el registro antes de crear otro ingreso."));
    }

    private void change(String id, UnaryOperator<ReceiptView> operation) {
        lock.lock();
        try {
            entries.put(id, operation.apply(entries.get(id)));
            publish();
        } finally { lock.unlock(); }
    }

    private void publish() { published = List.copyOf(entries.values()); }

    public ReceiptView find(String id) {
        // La admisión puede haber ofrecido el mensaje antes de publicar su ficha.
        lock.lock();
        try {
            ReceiptView entry = entries.get(id);
            if (entry == null) throw new ApiException(HttpStatus.NOT_FOUND, "La recepción no existe.");
            return enrich(entry, inventory.snapshot());
        } finally { lock.unlock(); }
    }

    private ReceiptView enrich(ReceiptView entry, InventorySnapshot snapshot) {
        var placement = snapshot.receipts().get(entry.id());
        if (placement == null) return entry;
        return new ReceiptView(entry.id(), entry.productId(), entry.productName(), entry.quantity(),
                placement.pendingUnits() == 0 ? ReceiptStatus.COMPLETED : ReceiptStatus.WAITING_SPACE,
                placement.placedUnits(), placement.pendingUnits(), entry.createdAt(), entry.scannedAt(), null);
    }

    /** Lectura sin bloqueos de recepción ni de inventario; adecuada para SSE bajo el bloqueo de estado. */
    public ReceiptReport report() {
        var entriesSnapshot = published;
        var stock = inventory.snapshot();
        var all = entriesSnapshot.stream().map(entry -> enrich(entry, stock)).toList();
        int completed = (int) all.stream().filter(entry -> entry.status() == ReceiptStatus.COMPLETED).count();
        int failed = (int) all.stream().filter(entry -> entry.status() == ReceiptStatus.ERROR).count();
        int waitingSpace = (int) all.stream().filter(entry -> entry.status() == ReceiptStatus.WAITING_SPACE).count();
        var recent = all.stream().sorted(Comparator.comparing(ReceiptView::createdAt).reversed()
                .thenComparing(ReceiptView::id)).limit(50).toList();
        return new ReceiptReport(running, incoming.size(), scanned.size(), settings.getQueueCapacity(),
                settings.getMaxOutstanding(), all.size(), all.size() - completed - failed,
                completed, failed, waitingSpace, recent);
    }

    @PreDestroy
    public void close() { stop(); }

    public record ReceiptView(String id, String productId, String productName, int quantity,
                              ReceiptStatus status, int placedUnits, int pendingUnits,
                              Instant createdAt, Instant scannedAt, String errorMessage) {
        ReceiptView withStatus(ReceiptStatus next, String error) {
            return new ReceiptView(id, productId, productName, quantity, next, placedUnits,
                    pendingUnits, createdAt, scannedAt, error);
        }
    }

    public record ReceiptReport(boolean running, int incomingQueueSize, int inventoryQueueSize,
                                int queueCapacity, int maxOutstanding, int total, int outstanding,
                                int completed, int failed, int waitingSpace, List<ReceiptView> items) {}
}
