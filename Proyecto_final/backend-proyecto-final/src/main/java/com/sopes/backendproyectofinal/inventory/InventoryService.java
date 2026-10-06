package com.sopes.backendproyectofinal.inventory;

import com.sopes.backendproyectofinal.catalog.CatalogService;
import com.sopes.backendproyectofinal.catalog.Product;
import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.warehouse.Lot;
import com.sopes.backendproyectofinal.warehouse.LotAllocation;
import com.sopes.backendproyectofinal.warehouse.Warehouse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Único responsable de modificar existencias y de colocar mercancía en el almacén.
 *
 * Ningún otro componente toca estas estructuras. Recepción, preparación y despacho hablan con este
 * servicio a través de sus métodos, nunca con el almacén directamente. Eso cumple el requisito de
 * aislamiento del inventario: dos áreas que se comunican mal no pueden dejar el almacén
 * inconsistente, porque no existe una segunda puerta de entrada.
 *
 * Todas las operaciones compuestas aplican el mismo patrón: se valida todo primero y solo después se
 * aplica, dentro de un único bloqueo. Ese es el mecanismo único que evita la sobreventa y las
 * reservas parciales.
 *
 * El bloqueo se toma siempre solo y se suelta antes de cualquier espera. En ningún momento se espera
 * por un recurso operativo, una entrada del usuario o una red mientras se sostiene. Por eso nunca
 * puede aparecer un ciclo de esperas entre inventario y cualquier otra parte del sistema.
 */
@Service
public class InventoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryService.class);

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Warehouse warehouse;
    private final CatalogService catalog;
    private final List<StockAvailabilityListener> listeners = new ArrayList<>();

    /** Existencias por producto. Solo este servicio las modifica. */
    private final Map<String, InventoryItem> items = new LinkedHashMap<>();

    /** Recepciones consolidadas por producto; alimenta el número de orden de cada lote. */
    private final Map<String, Long> receiptsByProduct = new LinkedHashMap<>();

    /** Mensajes ya aplicados y sus pendientes; el identificador impide altas duplicadas. */
    private final Map<String, ReceiptPlacement> receipts = new LinkedHashMap<>();

    /** Reserva activa por pedido, para que una reserva sea idempotente y no se duplique mercancía. */
    private final Map<String, StockReservation> reservationsByOrder = new LinkedHashMap<>();

    /** Retiros por pedido, para poder devolver exactamente las mismas unidades si el pedido falla. */
    private final Map<String, StockIssue> issuesByOrder = new LinkedHashMap<>();

    /** Pedidos ya recuperados, para que devolver mercancía dos veces no duplique existencias. */
    private final Set<String> recoveredOrders = new HashSet<>();

    /** Instantánea inmutable para lectores que no pueden tomar bloqueos. */
    private volatile InventorySnapshot published;
    private long revision;

    public InventoryService(CatalogService catalog, WarehouseSettings settings) {
        this.catalog = catalog;
        this.warehouse = new Warehouse(settings.locationCapacity(), settings.aisles(),
                settings.shelvesPerAisle(), settings.levelsPerShelf());
        catalog.findAll().forEach(product -> {
            items.put(product.id(), new InventoryItem(product.id(), 0, 0, 0));
            receiptsByProduct.put(product.id(), 0L);
        });
        settings.initialStock().forEach((productId, units) -> receive(productId, units));
        publish();
        LOGGER.info("Almacén inicializado: {} ubicaciones de {} unidades de volumen, {} ocupadas, "
                        + "{} unidades de mercancía",
                warehouse.locationCount(), settings.locationCapacity(), warehouse.occupiedVolume(),
                published.totalStock());
    }

    /** Registra a quién avisar cuando cambia la disponibilidad de existencias. */
    public void addListener(StockAvailabilityListener listener) {
        listeners.add(listener);
    }

    /**
     * Reserva las líneas de un pedido de forma atómica: todo o nada.
     *
     * Sobreventa. Diez trabajadores piden el mismo producto a la vez. Si cada uno comprobara las
     * existencias y después escribiera su reserva, dos podrían leer las mismas unidades disponibles
     * antes de que ninguno escriba, y las dos reservas pasarían la comprobación: el almacén acabaría
     * por encima de sus existencias reales. Aquí la comprobación y la escritura ocurren dentro del
     * mismo bloqueo, así que ninguna otra hebra puede intercalar una reserva entre las dos.
     *
     * Reserva parcial. Si una sola línea no tiene existencias, no se toca ninguna: se devuelve vacío
     * y el pedido vuelve a la cola. Reservar unas líneas y devolver otras dejaría mercancía
     * bloqueada sin motivo, y el pedido no podría completarse nunca.
     *
     * Invariante que se sostiene: para todo producto, lo reservado nunca pasa de lo existente.
     *
     * Las unidades se toman de las ubicaciones que guardan menos producto, para vaciar primero las
     * posiciones pequeñas en lugar de dejar restos por todas partes.
     */
    public Optional<StockReservation> reserve(Order order) {
        Optional<StockReservation> outcome;
        lock.lock();
        try {
            StockReservation existing = reservationsByOrder.get(order.id());
            if (existing != null) {
                outcome = Optional.of(existing);
            } else {
                outcome = createReservation(order);
                if (outcome.isPresent()) publish();
            }
        } finally {
            lock.unlock();
        }
        notifyListeners();
        return outcome;
    }

    /**
     * Valida todas las líneas y, solo si todas caben, las compromete de una vez.
     * Se ejecuta siempre dentro del bloqueo de este servicio.
     */
    private Optional<StockReservation> createReservation(Order order) {
        Map<String, Integer> required = requiredUnits(order);
        for (Map.Entry<String, Integer> need : required.entrySet()) {
            InventoryItem item = items.get(need.getKey());
            if (item == null || item.available() < need.getValue()) {
                return Optional.empty();
            }
        }
        List<StockReservation.ReservationLine> lines = new ArrayList<>();
        required.forEach((productId, units) -> lines.addAll(planWithdrawal(productId, units)));
        required.forEach((productId, units) -> addReserved(productId, units));
        StockReservation reservation = new StockReservation(order.id(), lines, Instant.now());
        reservationsByOrder.put(order.id(), reservation);
        return Optional.of(reservation);
    }

    /** Suma las líneas del pedido cuando el mismo producto aparece repetido. */
    private Map<String, Integer> requiredUnits(Order order) {
        Map<String, Integer> required = new LinkedHashMap<>();
        order.lines().forEach(line -> required.merge(line.productId(), line.quantity(), Integer::sum));
        return required;
    }

    /** Elige de qué ubicaciones saldrán las unidades, sin modificar nada todavía. */
    private List<StockReservation.ReservationLine> planWithdrawal(String productId, int units) {
        List<StockReservation.ReservationLine> segments = new ArrayList<>();
        int unitVolume = catalog.findById(productId).unitVolume();
        int remaining = units;
        for (String locationId : locationsLeastUnitsFirst(productId)) {
            if (remaining == 0) {
                break;
            }
            int present = warehouse.unitsAt(locationId, productId);
            // Cada reserva conserva sus ubicaciones: ninguna otra puede comprometer esas unidades.
            int committed = reservationsByOrder.values().stream()
                    .flatMap(reservation -> reservation.lines().stream())
                    .filter(line -> line.productId().equals(productId) && line.locationId().equals(locationId))
                    .mapToInt(StockReservation.ReservationLine::units).sum();
            present -= committed;
            if (present <= 0) {
                continue;
            }
            int quantity = Math.min(present, remaining);
            segments.add(new StockReservation.ReservationLine(productId, quantity, locationId, unitVolume));
            remaining -= quantity;
        }
        if (remaining != 0) {
            throw new IllegalStateException("No se pudo cubrir la reserva completa con unidades ubicadas.");
        }
        return segments;
    }

    /** Ubicaciones con menos unidades del producto primero, para consolidar y no fragmentar. */
    private List<String> locationsLeastUnitsFirst(String productId) {
        return warehouse.unitsByProduct().getOrDefault(productId, Map.of()).keySet().stream()
                .sorted(Comparator.comparingInt((String id) -> warehouse.unitsAt(id, productId))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** Suma lo comprometido con pedidos en curso, sin alterar el total de existencias. */
    private void addReserved(String productId, int units) {
        InventoryItem item = items.get(productId);
        items.put(productId, new InventoryItem(productId, item.stock(), item.reserved() + units,
                item.pendingPlacement()));
    }

    /**
     * Libera una reserva sin mover mercancía: las unidades vuelven a estar disponibles para otro
     * pedido. Se usa cuando el pedido se cancela antes de tomar su carga, y es idempotente porque la
     * reserva desaparece del registro en cuanto se libera.
     */
    public void release(StockReservation reservation) {
        lock.lock();
        try {
            StockReservation current = reservationsByOrder.remove(reservation.orderId());
            if (current == null) {
                return;
            }
            current.lines().forEach(line -> {
                InventoryItem item = items.get(line.productId());
                items.put(line.productId(), new InventoryItem(line.productId(), item.stock(),
                        item.reserved() - line.units(), item.pendingPlacement()));
            });
            publish();
        } finally {
            lock.unlock();
        }
        notifyListeners();
    }

    /**
     * Retira físicamente la mercancía y deja constancia de qué unidades salieron de dónde.
     *
     * El inventario baja de verdad aquí: a partir de este momento las unidades ya no están en el
     * almacén y el espacio que ocupaban queda libre. Ese espacio se reutiliza de inmediato para
     * colocar mercancía que estaba pendiente, para que una ubicación liberada no quede ociosa mientras
     * otras unidades esperan lugar.
     */
    public StockIssue consume(StockReservation reservation) {
        StockIssue issue;
        lock.lock();
        try {
            StockReservation current = reservationsByOrder.get(reservation.orderId());
            if (current == null) {
                throw new IllegalStateException("El pedido no tiene una reserva activa.");
            }
            // Se comprueba todo antes de retirar: una anomalía no debe dejar un retiro parcial.
            for (StockReservation.ReservationLine line : current.lines()) {
                if (warehouse.unitsAt(line.locationId(), line.productId()) < line.units()) {
                    throw new IllegalStateException("La ubicación no contiene todas las unidades reservadas.");
                }
            }
            List<StockIssue.IssuedLine> lines = new ArrayList<>();
            for (StockReservation.ReservationLine line : current.lines()) {
                InventoryItem item = items.get(line.productId());
                items.put(line.productId(), new InventoryItem(line.productId(), item.stock() - line.units(),
                        item.reserved() - line.units(), item.pendingPlacement()));
                warehouse.withdraw(line.locationId(), line.productId(), line.units(), line.unitVolume());
                lines.add(new StockIssue.IssuedLine(line.productId(), line.units(), line.locationId(),
                        line.unitVolume()));
            }
            reservationsByOrder.remove(current.orderId());
            placeAnyPendingUnits();
            issue = new StockIssue(current.orderId(), lines, Instant.now());
            issuesByOrder.put(current.orderId(), issue);
            publish();
        } finally {
            lock.unlock();
        }
        notifyListeners();
        return issue;
    }

    /** Trazabilidad del retiro de un pedido, si ya tomó su mercancía. */
    public Optional<StockIssue> findIssue(String orderId) {
        lock.lock();
        try {
            return Optional.ofNullable(issuesByOrder.get(orderId));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Devuelve al almacén la mercancía de un pedido que no pudo completarse.
     *
     * Esto es lo que hace recuperable un fallo. Sin el registro del retiro, un pedido que falla
     * después de tomar su carga dejaría el inventario descuadrado de forma irreversible. Aquí las
     * unidades vuelven primero a la ubicación de la que salieron, que es donde el encargado las dejó;
     * solo si ya no caben allí se recolocan en cualquier otra. Si el almacén está lleno, quedan como
     * mercancía pendiente de espacio en lugar de desaparecer.
     *
     * Devolver dos veces el mismo pedido no duplica existencias: el pedido queda marcado como
     * recuperado la primera vez.
     */
    public void recover(StockIssue issue) {
        lock.lock();
        try {
            if (!issue.equals(issuesByOrder.get(issue.orderId()))) {
                throw new IllegalArgumentException("El retiro no pertenece al inventario.");
            }
            if (!recoveredOrders.add(issue.orderId())) {
                return;
            }
            for (StockIssue.IssuedLine line : issue.lines()) {
                addStock(line.productId(), line.units(), line.locationId());
            }
            placeAnyPendingUnits();
            LOGGER.info("Mercancía del pedido {} devuelta al almacén", issue.orderId());
            publish();
        } finally {
            lock.unlock();
        }
        notifyListeners();
    }

    /**
     * Da de alta mercancía recibida.
     *
     * Lo que no cabe no se descarta: queda como mercancía pendiente de espacio y se reintenta cada vez
     * que un pedido libera una ubicación. Es el caso que el enunciado pide conservar en lugar de
     * perder.
     */
    public ReceiptResult receive(String productId, int units) {
        catalog.findById(productId);
        if (units <= 0) throw new IllegalArgumentException("La cantidad recibida debe ser positiva.");
        ReceiptResult result;
        lock.lock();
        try {
            int placedUnits = addStock(productId, units, null);
            receiptsByProduct.merge(productId, 1L, Long::sum);
            placeAnyPendingUnits();
            result = new ReceiptResult(productId, units, placedUnits, units - placedUnits);
            publish();
        } finally {
            lock.unlock();
        }
        notifyListeners();
        return result;
    }

    /**
     * El consumidor de la cola confirma un mensaje de forma atómica e idempotente.
     * El registro y las existencias se publican juntos: un reintento nunca vuelve a sumar unidades.
     */
    public ReceiptPlacement applyReceipt(StockReceipt message) {
        catalog.findById(message.productId());
        ReceiptPlacement result;
        lock.lock();
        try {
            ReceiptPlacement previous = receipts.get(message.id());
            if (previous != null) {
                if (!previous.message().equals(message)) {
                    throw new IllegalArgumentException("El identificador de recepción ya tiene otros datos.");
                }
                return previous;
            }
            // Validar antes de registrar el mensaje evita dejar un alta parcial por desbordamiento.
            Math.addExact(items.get(message.productId()).stock(), message.units());
            receipts.put(message.id(), new ReceiptPlacement(message, message.units()));
            addStock(message.productId(), message.units(), null);
            receiptsByProduct.merge(message.productId(), 1L, Long::sum);
            publish();
            result = receipts.get(message.id());
        } finally { lock.unlock(); }
        notifyListeners();
        return result;
    }

    /**
     * Suma existencias y las coloca en cuanto haya sitio.
     *
     * Primero se cuentan como pendientes y después se descuentan las que sí encontraron
     * lugar. Así la cuenta cuadra siempre: pendientes más colocadas es exactamente lo recibido, ni
     * una unidad de más ni una de menos.
     */
    private int addStock(String productId, int units, String preferredLocationId) {
        InventoryItem item = items.get(productId);
        items.put(productId, new InventoryItem(productId, Math.addExact(item.stock(), units), item.reserved(),
                Math.addExact(item.pendingPlacement(), units)));
        return placeUpTo(productId, units, preferredLocationId);
    }

    /**
     * Coloca hasta el límite indicado y descuenta lo colocado de la mercancía pendiente.
     *
     * Si se indica la ubicación de origen se intenta primero allí, que es lo que hace la recuperación
     * exacta de un pedido fallido. Lo que sobra se reparte con el criterio general de consolidación y
     * mejor ajuste; lo que sigue sin caber permanece pendiente.
     */
    private int placeUpTo(String productId, int units, String preferredLocationId) {
        if (units < 1) {
            return 0;
        }
        Product product = catalog.findById(productId);
        int placed = preferredLocationId == null ? 0
                : warehouse.storeAt(product, preferredLocationId, units);
        if (placed < units) {
            placed += warehouse.store(product, units - placed).stream()
                    .mapToInt(LotAllocation::units).sum();
        }
        if (placed > 0) {
            InventoryItem item = items.get(productId);
            // Primero se colocan las pendientes sin mensaje (carga inicial o recuperación).
            // Después se confirma por llegada lo colocado para cada recepción del mismo producto.
            int tracked = receipts.values().stream()
                    .filter(receipt -> receipt.message().productId().equals(productId))
                    .mapToInt(ReceiptPlacement::pendingUnits).sum();
            int remaining = Math.max(0, placed - (item.pendingPlacement() - tracked));
            for (var entry : receipts.entrySet()) {
                ReceiptPlacement receipt = entry.getValue();
                if (remaining == 0) break;
                if (!receipt.message().productId().equals(productId)) continue;
                int allocated = Math.min(remaining, receipt.pendingUnits());
                entry.setValue(new ReceiptPlacement(receipt.message(), receipt.pendingUnits() - allocated));
                remaining -= allocated;
            }
            items.put(productId, new InventoryItem(productId, item.stock(), item.reserved(),
                    Math.max(0, item.pendingPlacement() - placed)));
        }
        return placed;
    }

    /**
     * Reintenta colocar toda la mercancía pendiente de cualquier producto.
     *
     * Se llama cuando el almacén libera espacio. Puede servir para un producto distinto del que dejó
     * el hueco, porque una ubicación vacía sirve igual para cualquier mercancía. El proceso se repite
     * mientras quede avance posible y termina siempre: cada colocación exitosa descuenta pendientes,
     * así que el número de iteraciones está acotado por el total de pendientes.
     */
    private void placeAnyPendingUnits() {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (String productId : new ArrayList<>(items.keySet())) {
                int pending = items.get(productId).pendingPlacement();
                if (pending > 0 && placeUpTo(productId, pending, null) > 0) {
                    progress = true;
                }
            }
        }
    }

    /**
     * Publica una instantánea coherente para los lectores que no toman bloqueos.
     *
     * Los lotes se derivan del almacén físico en lugar de mantenerse por separado. Así la instantánea
     * no puede discrepar de la realidad: si una unidad salió de una ubicación, el lote publicado
     * refleja que ya no está ahí. La publicación ocurre siempre antes de liberar el bloqueo, nunca
     * durante una espera, y el campo es volátil para que la publicación sea visible de inmediato.
     */
    private void publish() {
        List<Lot> lots = new ArrayList<>();
        Map<String, Map<String, Integer>> stored = warehouse.unitsByProduct();
        for (Product product : catalog.findAll()) {
            Map<String, Integer> byLocation = stored.get(product.id());
            if (byLocation == null || byLocation.isEmpty()) {
                continue;
            }
            List<LotAllocation> allocations = byLocation.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new LotAllocation(entry.getKey(), entry.getValue(),
                            entry.getValue() * product.unitVolume()))
                    .toList();
            lots.add(Lot.of(product.id(), allocations, receiptsByProduct.getOrDefault(product.id(), 0L)));
        }
        published = new InventorySnapshot(++revision, Instant.now(), new LinkedHashMap<>(items),
                lots, warehouse.locations(), warehouse.occupiedVolume(), warehouse.availableVolume(), receipts);
    }

    /** Avisa a los planificadores una vez liberado el bloqueo, para no encadenar bloqueos ajenos. */
    private void notifyListeners() {
        listeners.forEach(StockAvailabilityListener::onAvailabilityChanged);
    }

    /** Instantánea publicada; no toma el bloqueo y por eso no puede quedar atrapada esperando. */
    public InventorySnapshot snapshot() {
        return published;
    }

    /** Existencias por producto, para la consulta del inventario. */
    public List<InventoryReport> report() {
        return report(snapshot());
    }

    public List<InventoryReport> report(InventorySnapshot current) {
        List<Lot> lots = current.lots();
        return catalog.findAll().stream().map(product -> {
            InventoryItem item = current.item(product.id());
            int locations = lots.stream().filter(lot -> lot.productId().equals(product.id()))
                    .mapToInt(Lot::locationCount).sum();
            return new InventoryReport(product.id(), product.name(), product.merchandiseType().name(),
                    product.unitVolume(), item.stock(), item.reserved(), item.available(),
                    item.pendingPlacement(), locations);
        }).toList();
    }

    /** Capacidad, ocupación y distribución real de los lotes, para la consulta del almacén. */
    public WarehouseReport warehouseReport() {
        return warehouseReport(snapshot());
    }

    public WarehouseReport warehouseReport(InventorySnapshot current) {
        List<Location> locationViews = current.locations().stream()
                .map(location -> new Location(location.id(), location.capacity(), location.usedVolume()))
                .toList();
        List<LotReport> lots = current.lots().stream()
                .map(lot -> new LotReport(lot.id(), lot.productId(),
                        catalog.findById(lot.productId()).name(), lot.units(), lot.locationCount(),
                        lot.isSplit(), lot.usedVolume(), lot.receipts(),
                        lot.allocations().stream().map(allocation -> new LocationAllocationReport(
                                allocation.locationId(), allocation.units(),
                                allocation.usedVolume())).toList()))
                .toList();
        int volumePerLocation = locationViews.isEmpty() ? 0 : locationViews.get(0).capacity();
        int splitLots = (int) current.lots().stream().filter(Lot::isSplit).count();
        return new WarehouseReport(current.locations().size(), volumePerLocation, current.occupiedVolume(),
                current.availableVolume(),
                (int) locationViews.stream().filter(location -> location.usedVolume() > 0).count(),
                splitLots, locationViews, lots);
    }

    /** Resultado de dar de alta mercancía recibida: cuánto entró y cuánto quedó sin lugar. */
    public record ReceiptResult(String productId, int units, int placedUnits, int pendingUnits) {}

    /** Existencias de un producto con los datos del catálogo y su distribución. */
    public record InventoryReport(String productId, String productName, String merchandiseType,
                                  int unitVolume, int stock, int reserved, int available,
                                  int pendingPlacement, int locations) {}

    /** Capacidad y ocupación del almacén, con el reparto de los lotes. */
    public record WarehouseReport(int locationCount, int volumePerLocation, int occupiedVolume,
                                  int availableVolume, int locationsInUse, int splitLots,
                                  List<Location> locations, List<LotReport> lots) {}

    /** Una ubicación física y cuánto ocupa. */
    public record Location(String id, int capacity, int usedVolume) {
        public int availableVolume() {
            return capacity - usedVolume;
        }
    }

    /** Un lote con la lista de ubicaciones donde está repartido. */
    public record LotReport(String id, String productId, String productName, int units, int locationCount,
                            boolean split, int usedVolume, long receipts,
                            List<LocationAllocationReport> allocations) {}

    /** Unidades de un lote en una ubicación concreta. */
    public record LocationAllocationReport(String locationId, int units, int usedVolume) {}
}
