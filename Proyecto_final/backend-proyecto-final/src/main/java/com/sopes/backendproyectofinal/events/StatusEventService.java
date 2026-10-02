package com.sopes.backendproyectofinal.events;

import com.sopes.backendproyectofinal.shared.ApiException;
import com.sopes.backendproyectofinal.status.StatusResponse;
import com.sopes.backendproyectofinal.status.StatusService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class StatusEventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusEventService.class);
    private final StatusService status;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService publisher = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "redxela-sse-publisher"); thread.setDaemon(true); return thread;
    });
    private final ThreadPoolExecutor senders = new ThreadPoolExecutor(0, 16, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), task -> {
                Thread thread = new Thread(task, "redxela-sse-sender"); thread.setDaemon(true); return thread;
            });

    public StatusEventService(StatusService status) {
        this.status = status;
        publisher.scheduleAtFixedRate(this::publish, 300, 300, TimeUnit.MILLISECONDS);
    }

    public synchronized SseEmitter subscribe() {
        if (clients.size() >= 16) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Hay demasiadas conexiones abiertas. Cierra otra pestaña e intenta nuevamente.");
        Client client = new Client();
        clients.put(client.id, client);
        client.emitter.onCompletion(client::remove);
        client.emitter.onTimeout(client::close);
        client.emitter.onError(error -> client.remove());
        // Una reconexión siempre recibe una instantánea completa, sin depender del historial del cliente.
        client.offer(status.getStatus());
        return client.emitter;
    }

    private void publish() {
        try {
            if (clients.isEmpty()) return;
            StatusResponse snapshot = status.getStatus();
            for (Client client : clients.values()) {
                if (client.sendingSince != 0 && System.nanoTime() - client.sendingSince > TimeUnit.SECONDS.toNanos(10)) {
                    client.close();
                } else client.offer(snapshot);
            }
        } catch (Exception exception) {
            LOGGER.error("No se pudo publicar el estado de la simulación", exception);
        }
    }

    /** Un solo estado pendiente por cliente: un navegador lento no acumula eventos ni bloquea el motor. */
    private final class Client {
        private final String id = UUID.randomUUID().toString();
        private final SseEmitter emitter = new SseEmitter(60_000L);
        private final AtomicReference<StatusResponse> latest = new AtomicReference<>();
        private final AtomicBoolean draining = new AtomicBoolean();
        private volatile long sendingSince;

        private void offer(StatusResponse snapshot) {
            if (!clients.containsKey(id)) return;
            latest.accumulateAndGet(snapshot, (previous, incoming) ->
                    previous == null || incoming.sequence() > previous.sequence() ? incoming : previous);
            schedule();
        }

        private void schedule() {
            if (!draining.compareAndSet(false, true)) return;
            try { senders.execute(this::send); }
            catch (RejectedExecutionException exception) { draining.set(false); close(); }
        }

        private void send() {
            try {
                StatusResponse snapshot;
                while (clients.containsKey(id) && (snapshot = latest.getAndSet(null)) != null) {
                    sendingSince = System.nanoTime();
                    emitter.send(SseEmitter.event().name("status").id(snapshot.runId() + ":" + snapshot.sequence())
                            .reconnectTime(1000).data(snapshot));
                    sendingSince = 0;
                }
            } catch (IOException | IllegalStateException exception) {
                remove();
            } finally {
                sendingSince = 0;
                draining.set(false);
                if (clients.containsKey(id) && latest.get() != null) schedule();
            }
        }

        private void remove() { clients.remove(id); latest.set(null); }
        private void close() { remove(); emitter.complete(); }
    }

    @PreDestroy
    public void close() {
        publisher.shutdownNow();
        clients.values().forEach(Client::close);
        senders.shutdownNow();
    }
}
