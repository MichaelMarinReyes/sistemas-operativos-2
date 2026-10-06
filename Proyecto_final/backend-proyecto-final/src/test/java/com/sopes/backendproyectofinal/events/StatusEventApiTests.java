package com.sopes.backendproyectofinal.events;

import com.jayway.jsonpath.JsonPath;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifica el contrato de la transmisión en vivo contra un servidor real: al conectarse el cliente
 * recibe una instantánea completa, las secuencias avanzan y una desconexión abrupta no afecta al resto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatusEventApiTests {
    @LocalServerPort
    private int port;

    /** Lee un campo del payload JSON del evento sin depender de una biblioteca de mapeo. */
    private record Event(String name, String id, String data) {
        Object field(String path) throws Exception { return JsonPath.read(data, path); }
    }

    @Test
    void sendsCompleteSnapshotOnConnectAndOnlyNewerSequences() throws Exception {
        String customer = post("/api/customers", """
                {"name": "Cliente SSE", "serviceLevel": 3}""");
        String customerId = (String) JsonPath.read(customer, "$.id");
        assertThat(customerId).isNotBlank();
        post("/api/orders", """
                {"customerId": "%s", "lines": [{"productId": "GEN-01", "quantity": 5}]}""".formatted(customerId));

        List<Event> events = readEvents(4, Duration.ofSeconds(6));
        assertThat(events).hasSizeGreaterThanOrEqualTo(4);
        assertThat(events).allMatch(event -> event.name().equals("status"));
        assertThat(events.get(0).id()).contains(":");

        // La instantánea inicial es completa: el cliente no depende del historial que tenía antes.
        Event first = events.get(0);
        assertThat(((Number) first.field("$.contractVersion")).intValue()).isEqualTo(5);
        assertThat((List<?>) first.field("$.receipts.items")).isNotNull();
        assertThat((String) first.field("$.runId")).isNotBlank();
        assertThat(((Number) first.field("$.sequence")).intValue()).isPositive();
        assertThat((String) first.field("$.generatedAt")).isNotBlank();
        assertThat((List<?>) first.field("$.resources")).hasSize(6);
        assertThat((List<?>) first.field("$.resourceRequests")).isNotNull();
        assertThat((List<?>) first.field("$.activeOrders")).isNotNull();
        assertThat((List<?>) first.field("$.waitingOrders")).isNotNull();
        // El resumen de inventario viaja en cada instantánea, así el tablero se actualiza solo.
        assertThat(((Number) first.field("$.inventory.stock")).intValue()).isPositive();
        assertThat(((Number) first.field("$.inventory.inventoryRevision")).longValue()).isPositive();
        assertThat(((Number) first.field("$.warehouse.locations")).intValue()).isEqualTo(100);
        assertThat(((Number) first.field("$.warehouse.occupiedVolume")).intValue()).isPositive();

        long previous = 0;
        for (Event event : events) {
            long sequence = ((Number) event.field("$.sequence")).longValue();
            assertThat(sequence).isGreaterThan(previous);
            previous = sequence;
        }
    }

    @Test
    void abruptClientDisconnectDoesNotAffectOtherSubscribers() throws Exception {
        // Una lectura cortada imita el cierre de una pestaña; no debe propagar un fallo al servidor.
        assertThatCode(() -> {
            HttpURLConnection connection = open();
            connection.setReadTimeout(300);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                reader.readLine();
            } catch (IOException expectedOnCutConnection) {
                // El corte abrupto es justamente lo que esta prueba provoca.
            } finally { connection.disconnect(); }
        }).doesNotThrowAnyException();

        List<Event> events = readEvents(2, Duration.ofSeconds(6));
        assertThat(events).hasSizeGreaterThanOrEqualTo(2);
        assertThat((String) events.get(0).field("$.runId")).isNotBlank();
    }

    /** Registra datos por HTTP para provocar cambios observables en la transmisión. */
    private String post(String path, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:" + port + path).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(5000);
        try (var output = connection.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }
        assertThat(connection.getResponseCode()).isLessThan(300);
        try (var input = connection.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } finally { connection.disconnect(); }
    }

    private HttpURLConnection open() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url()).toURL().openConnection();
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(5000);
        connection.connect();
        return connection;
    }

    /** Lee eventos hasta alcanzar la cantidad esperada o agotar el plazo, y cierra la conexión. */
    private List<Event> readEvents(int expected, Duration timeout) throws Exception {
        List<Event> events = new ArrayList<>();
        HttpURLConnection connection = open();
        long deadline = System.nanoTime() + timeout.toNanos();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String name = null;
            String id = null;
            StringBuilder data = new StringBuilder();
            while (System.nanoTime() < deadline && events.size() < expected) {
                String line = reader.readLine();
                if (line == null) break;
                if (line.startsWith("event:")) name = line.substring(6).trim();
                else if (line.startsWith("id:")) id = line.substring(3).trim();
                else if (line.startsWith("data:")) data.append(line.substring(5).trim());
                else if (line.isEmpty() && data.length() > 0) {
                    events.add(new Event(name, id, data.toString()));
                    name = null;
                    id = null;
                    data.setLength(0);
                }
            }
        } finally { connection.disconnect(); }
        return events;
    }

    private String url() { return "http://localhost:" + port + "/api/events"; }
}
