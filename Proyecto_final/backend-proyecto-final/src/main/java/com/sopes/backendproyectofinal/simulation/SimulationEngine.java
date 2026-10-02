package com.sopes.backendproyectofinal.simulation;

import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.orders.OrderRepository;
import com.sopes.backendproyectofinal.orders.OrderStatus;
import com.sopes.backendproyectofinal.resources.ResourceManager;
import com.sopes.backendproyectofinal.shared.ApiException;
import com.sopes.backendproyectofinal.shared.SimulationState;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SimulationEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationEngine.class);
    private final OrderRepository orders;
    private final ResourceManager resources;
    private final StagePlanner planner;
    private final SimulationClock clock;
    private final SimulationSettings settings;
    private final SimulationState state;
    private volatile EngineStatus status = EngineStatus.NOT_STARTED;
    private ExecutorService workers;

    public SimulationEngine(OrderRepository orders, ResourceManager resources, StagePlanner planner,
                            SimulationClock clock, SimulationSettings settings, SimulationState state) {
        this.orders = orders; this.resources = resources; this.planner = planner;
        this.clock = clock; this.settings = settings; this.state = state;
    }

    /** Un único grupo de trabajadores por motor, aunque varias pestañas soliciten iniciar. */
    public synchronized EngineStatus start() {
        if (status == EngineStatus.RUNNING) return status;
        if (status == EngineStatus.STOPPING) throw new ApiException(HttpStatus.CONFLICT, "El motor todavía se está deteniendo.");
        state.lock.lock();
        try {
            AtomicInteger counter = new AtomicInteger();
            workers = Executors.newFixedThreadPool(settings.getWorkerCount(), task ->
                    new Thread(task, "redxela-worker-" + counter.incrementAndGet()));
            status = EngineStatus.RUNNING;
            clock.start();
            for (int index = 0; index < settings.getWorkerCount(); index++) workers.submit(this::work);
            return status;
        } finally { state.lock.unlock(); }
    }

    /** Interrumpe los pedidos activos y conserva los que todavía no se retiraron de la cola. */
    public synchronized EngineStatus stop() {
        if (workers == null || status == EngineStatus.STOPPED) return status;
        status = EngineStatus.STOPPING;
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.CONFLICT, "El motor sigue liberando sus recursos. Intenta detenerlo nuevamente.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.CONFLICT, "Se interrumpió la espera del cierre del motor.");
        }
        state.lock.lock();
        try { clock.stop(); status = EngineStatus.STOPPED; }
        finally { state.lock.unlock(); }
        return status;
    }

    public EngineStatus status() { return status; }

    private void work() {
        while (!Thread.currentThread().isInterrupted() && status == EngineStatus.RUNNING) {
            Order order = null;
            try {
                order = orders.awaitNext();
                process(order);
            } catch (InterruptedException exception) {
                if (order != null) fail(order.id(), "Procesamiento interrumpido al detener la simulación.");
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                LOGGER.error("Falló el procesamiento de un pedido", exception);
                if (order != null) fail(order.id(), "No se pudo completar el pedido por un error de procesamiento.");
            }
        }
    }

    private void fail(String id, String message) {
        orders.update(id, current -> current.status() == OrderStatus.COMPLETED || current.status() == OrderStatus.ERROR
                ? current : current.fail(message));
    }

    private void process(Order order) throws InterruptedException {
        List<StagePlanner.Stage> stages = planner.plan(order);
        double total = stages.stream().mapToDouble(StagePlanner.Stage::simulatedSeconds).sum();
        double finished = 0;
        for (StagePlanner.Stage stage : stages) {
            double completedWork = finished;
            orders.update(order.id(), current -> current.transitionTo(OrderStatus.WAITING_RESOURCES)
                    .advance(stage.stage(), completedWork / total * 100, null));
            try (ResourceManager.Lease ignored = resources.acquire(order.id(), stage.resources())) {
                orders.update(order.id(), current -> current.transitionTo(OrderStatus.PROCESSING)
                        .advance(stage.stage(), completedWork / total * 100, (total - completedWork) / 60));
                long duration = clock.realNanos(stage.simulatedSeconds());
                long started = System.nanoTime();
                while (true) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    long elapsed = System.nanoTime() - started;
                    double fraction = Math.min(1, (double) elapsed / duration);
                    double work = completedWork + stage.simulatedSeconds() * fraction;
                    orders.update(order.id(), current -> current.advance(stage.stage(),
                            Math.min(99.99, work / total * 100), Math.max(0, (total - work) / 60)));
                    if (elapsed >= duration) break;
                    TimeUnit.NANOSECONDS.sleep(Math.min(TimeUnit.MILLISECONDS.toNanos(50), duration - elapsed));
                }
            }
            finished += stage.simulatedSeconds();
        }
        orders.update(order.id(), current -> current.transitionTo(OrderStatus.COMPLETED));
    }

    @PreDestroy
    public void close() { stop(); }

    public enum EngineStatus { NOT_STARTED, RUNNING, STOPPING, STOPPED }
}
