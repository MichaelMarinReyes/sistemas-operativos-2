package com.sopes.backendproyectofinal.orders;

import com.sopes.backendproyectofinal.inventory.InventoryService;
import com.sopes.backendproyectofinal.inventory.StockAvailabilityListener;
import com.sopes.backendproyectofinal.shared.SimulationState;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Repository;

/**
 * Almacén de pedidos y punto de selección para los trabajadores.
 *
 * Todas las operaciones se serializan con el bloqueo de SimulationState, y la captura de una
 * instantánea del inventario nunca se hace tomando el bloqueo del inventario. Esa es la razón por la
 * que el servicio de inventario publica una instantánea inmutable en un campo volátil: leerla no
 * requiere ningún bloqueo, así que es imposible anidar el bloqueo de pedidos con el de inventario y
 * provocar un interbloqueo por espera mutua en sentidos contrarios.
 */
@Repository
public class OrderRepository implements StockAvailabilityListener {
    private final Map<String, Order> orders = new HashMap<>();
    private final SimulationState state;
    private final InventoryService inventory;
    private final Condition orderAvailable;
    private long revision;

    public OrderRepository(SimulationState state, InventoryService inventory) {
        this.state = state;
        this.inventory = inventory;
        this.orderAvailable = state.lock.newCondition();
        inventory.addListener(this);
    }

    public void save(Order order) {
        state.lock.lock();
        try {
            orders.put(order.id(), order);
            revision++;
            orderAvailable.signalAll();
        } finally {
            state.lock.unlock();
        }
    }

    public Optional<Order> findById(String id) {
        state.lock.lock();
        try {
            return Optional.ofNullable(orders.get(id));
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * Retira de la cola el mejor pedido elegible y lo devuelve en estado QUEUED.
     *
     * Elegible significa que tiene existencias suficientes para todas sus líneas. La comprobación usa
     * la instantánea publicada del inventario, así que no toma el bloqueo del inventario y no puede
     * quedar atrapada esperando. Un pedido sin existencias no bloquea al resto de la cola: el
     * trabajador simplemente sigue esperando y otro puede avanzar.
     *
     * La instantánea puede estar desactualizada, porque entre que se lee y se hace la reserva otro
     * pedido puede haberse llevado las mismas unidades. Por eso la reserva vuelve a comprobar y, si
     * pierde la carrera, devuelve el pedido a WAITING_STOCK. La comprobación de la instantánea es una
     * pista para no despertar trabajadores inútiles; la que manda es la de la reserva.
     *
     * Selección, transición y escritura ocurren en una sola región crítica, de modo que dos
     * trabajadores nunca retiran el mismo pedido.
     */
    public Order awaitNextEligible() throws InterruptedException {
        state.lock.lockInterruptibly();
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                var snapshot = inventory.snapshot();
                for (Order candidate : List.copyOf(orders.values())) {
                    if (candidate.status() == OrderStatus.CREATED && !snapshot.canSatisfy(candidate)) {
                        orders.put(candidate.id(), candidate.transitionTo(OrderStatus.WAITING_STOCK));
                        revision++;
                    }
                }
                Optional<Order> next = orders.values().stream()
                        .filter(order -> order.status() == OrderStatus.CREATED
                                || order.status() == OrderStatus.WAITING_STOCK)
                        .filter(order -> snapshot.canSatisfy(order))
                        .min(Comparator.comparingInt(Order::serviceLevel)
                                .thenComparing(Order::createdAt)
                                .thenComparing(Order::id));
                if (next.isPresent()) {
                    Order queued = next.get().transitionTo(OrderStatus.QUEUED);
                    orders.put(queued.id(), queued);
                    revision++;
                    return queued;
                }
                orderAvailable.await();
            }
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * Devuelve un pedido a espera de existencias porque perdió la reserva contra otro trabajador.
     * Es la transición que cierra la carrera descrita en awaitNextEligible.
     */
    public void returnForStock(String id) {
        update(id, current -> current.status() == OrderStatus.QUEUED
                ? current.transitionTo(OrderStatus.WAITING_STOCK) : current);
    }

    public void update(String id, UnaryOperator<Order> change) {
        state.lock.lock();
        try {
            Order previous = orders.get(id);
            if (previous == null) {
                return;
            }
            Order updated = change.apply(previous);
            orders.put(id, updated);
            if (previous.status() != updated.status()) {
                revision++;
            }
        } finally {
            state.lock.unlock();
        }
    }

    public long revision() {
        state.lock.lock();
        try {
            return revision;
        } finally {
            state.lock.unlock();
        }
    }

    /** Despierta a los trabajadores en espera cuando cambia la disponibilidad de existencias. */
    @Override
    public void onAvailabilityChanged() {
        state.lock.lock();
        try {
            orderAvailable.signalAll();
        } finally {
            state.lock.unlock();
        }
    }

    /** Devuelve una copia ordenada, sin exponer la colección mutable compartida. */
    public List<Order> findAll() {
        state.lock.lock();
        try {
            return orders.values().stream()
                    .sorted(Comparator.comparing(Order::createdAt).reversed().thenComparing(Order::id))
                    .toList();
        } finally {
            state.lock.unlock();
        }
    }
}
