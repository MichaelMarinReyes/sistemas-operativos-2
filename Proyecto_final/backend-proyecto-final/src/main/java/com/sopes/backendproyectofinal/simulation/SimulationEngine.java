package com.sopes.backendproyectofinal.simulation;

import com.sopes.backendproyectofinal.inventory.InventoryService;
import com.sopes.backendproyectofinal.inventory.StockIssue;
import com.sopes.backendproyectofinal.inventory.StockReservation;
import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.orders.OrderRepository;
import com.sopes.backendproyectofinal.orders.OrderStage;
import com.sopes.backendproyectofinal.orders.OrderStatus;
import com.sopes.backendproyectofinal.resources.ResourceManager;
import com.sopes.backendproyectofinal.shared.ApiException;
import com.sopes.backendproyectofinal.shared.SimulationState;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Motor que retira pedidos elegibles y los conduce hasta completarse usando recursos limitados.
 *
 * El orden de las operaciones es deliberado y es la regla que evita el interbloqueo del enunciado:
 * primero se reservan las existencias, que es una operación corta con su propio bloqueo, y solo
 * después se piden los recursos operativos, que sí pueden tardar. En ningún momento se espera por un
 * recurso operativo mientras se sostiene el bloqueo del inventario. El bloqueo del inventario es una
 * hoja: nunca se sostiene junto a otro.
 */
@Service
public class SimulationEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationEngine.class);

    private final OrderRepository orders;
    private final InventoryService inventory;
    private final ResourceManager resources;
    private final StagePlanner planner;
    private final SimulationClock clock;
    private final SimulationSettings settings;
    private final SimulationState state;
    private volatile EngineStatus status = EngineStatus.NOT_STARTED;
    private ExecutorService workers;

    public SimulationEngine(OrderRepository orders, InventoryService inventory, ResourceManager resources,
                            StagePlanner planner, SimulationClock clock, SimulationSettings settings,
                            SimulationState state) {
        this.orders = orders;
        this.inventory = inventory;
        this.resources = resources;
        this.planner = planner;
        this.clock = clock;
        this.settings = settings;
        this.state = state;
    }

    /** Un único grupo de trabajadores por motor, aunque varias pestañas soliciten iniciar. */
    public synchronized EngineStatus start() {
        if (status == EngineStatus.RUNNING) {
            return status;
        }
        if (status == EngineStatus.STOPPING) {
            throw new ApiException(HttpStatus.CONFLICT, "El motor todavía se está deteniendo.");
        }
        state.lock.lock();
        try {
            AtomicInteger counter = new AtomicInteger();
            workers = Executors.newFixedThreadPool(settings.getWorkerCount(), task ->
                    new Thread(task, "redxela-worker-" + counter.incrementAndGet()));
            status = EngineStatus.RUNNING;
            clock.start();
            for (int index = 0; index < settings.getWorkerCount(); index++) {
                workers.submit(this::work);
            }
            return status;
        } finally {
            state.lock.unlock();
        }
    }

    /** Interrumpe los pedidos activos y conserva los que todavía no se retiraron de la cola. */
    public synchronized EngineStatus stop() {
        if (workers == null || status == EngineStatus.STOPPED) {
            return status;
        }
        status = EngineStatus.STOPPING;
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "El motor sigue liberando sus recursos. Intenta detenerlo nuevamente.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.CONFLICT, "Se interrumpió la espera del cierre del motor.");
        }
        state.lock.lock();
        try {
            clock.stop();
            status = EngineStatus.STOPPED;
        } finally {
            state.lock.unlock();
        }
        return status;
    }

    public EngineStatus status() {
        return status;
    }

    /**
     * Ciclo de un trabajador: espera un pedido elegible, reserva su mercancía y lo procesa.
     *
     * Si la reserva se pierde contra otro trabajador, el pedido vuelve a la espera y este hilo sigue
     * buscando trabajo. Así un pedido sin existencias nunca ocupa un trabajador.
     */
    private void work() {
        while (!Thread.currentThread().isInterrupted() && status == EngineStatus.RUNNING) {
            Order order = null;
            StockReservation reservation = null;
            StockIssue issue = null;
            try {
                order = orders.awaitNextEligible();
                Optional<StockReservation> reserved = inventory.reserve(order);
                if (reserved.isEmpty()) {
                    orders.returnForStock(order.id());
                    continue;
                }
                reservation = reserved.get();
                issue = process(order, reservation);
            } catch (InterruptedException exception) {
                if (reservation != null) {
                    releaseBack(order, reservation);
                }
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                LOGGER.error("Falló el procesamiento de un pedido", exception);
                if (order != null) {
                    releaseBack(order, reservation);
                }
            }
        }
    }

    /**
     * Devuelve la mercancía reservada al inventario cuando el pedido no llegó a completarse.
     *
     * Si ya se había retirado del almacén, la trazabilidad del retiro permite devolver exactamente
     * las mismas unidades a las mismas ubicaciones. Si solo estaba reservada, basta con liberarla.
     * Cualquiera de los dos caminos conserva el inventario consistente.
     */
    private void releaseBack(Order order, StockReservation reservation) {
        if (order == null) {
            return;
        }
        orders.update(order.id(), current -> current.status() == OrderStatus.COMPLETED
                || current.status() == OrderStatus.ERROR ? current
                        : current.fail("No se completó el pedido; su mercancía se devolvió al inventario."));
        recoverIssuedStock(order.id());
        if (reservation != null) {
            inventory.release(reservation);
        }
    }

    /** Recupera el retiro registrado para el pedido, si lo hubo. */
    private void recoverIssuedStock(String orderId) {
        inventory.findIssue(orderId).ifPresent(inventory::recover);
    }

    /**
     * Ejecuta las etapas del pedido y devuelve la trazabilidad del retiro de mercancía.
     *
     * La mercancía se toma físicamente al empezar la preparación, que es cuando un encargado va por
     * ella. A partir de ahí el inventario baja de verdad y el espacio queda libre para reutilizar.
     */
    private StockIssue process(Order order, StockReservation reservation) throws InterruptedException {
        List<StagePlanner.Stage> stages = planner.plan(order);
        double total = stages.stream().mapToDouble(StagePlanner.Stage::simulatedSeconds).sum();
        double finished = 0;
        StockIssue issue = null;
        for (StagePlanner.Stage stage : stages) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            double completedWork = finished;
            orders.update(order.id(), current -> current.transitionTo(OrderStatus.WAITING_RESOURCES)
                    .advance(stage.stage(), completedWork / total * 100, null));
            try (ResourceManager.Lease ignored = resources.acquire(order.id(), stage.resources())) {
                if (issue == null && stage.stage() == OrderStage.PREPARING) {
                    issue = inventory.consume(reservation);
                }
                orders.update(order.id(), current -> current.transitionTo(OrderStatus.PROCESSING)
                        .advance(stage.stage(), completedWork / total * 100, (total - completedWork) / 60));
                runStage(order.id(), stage, completedWork, total);
            }
            finished += stage.simulatedSeconds();
        }
        orders.update(order.id(), current -> current.transitionTo(OrderStatus.COMPLETED));
        return issue;
    }

    /** Avanza el progreso de una etapa en pasos cortos hasta consumir su tiempo simulado. */
    private void runStage(String orderId, StagePlanner.Stage stage, double completedWork, double total)
            throws InterruptedException {
        long duration = clock.realNanos(stage.simulatedSeconds());
        long started = System.nanoTime();
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            long elapsed = System.nanoTime() - started;
            double fraction = Math.min(1, (double) elapsed / duration);
            double work = completedWork + stage.simulatedSeconds() * fraction;
            orders.update(orderId, current -> current.advance(stage.stage(),
                    Math.min(99.99, work / total * 100), Math.max(0, (total - work) / 60)));
            if (elapsed >= duration) {
                return;
            }
            TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(50), duration - elapsed));
        }
    }

    @PreDestroy
    public void close() {
        stop();
    }

    public enum EngineStatus { NOT_STARTED, RUNNING, STOPPING, STOPPED }
}
