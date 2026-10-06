package com.sopes.backendproyectofinal.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica el contrato de las consultas de inventario y almacén.
 *
 * Lo que se comprueba es que las rutas devuelven el estado actual ya calculado y que el almacén
 * expone el reparto real de cada lote entre ubicaciones, que es lo que hace visible que el
 * almacenamiento no exige contigüidad.
 *
 * Todas las lecturas usan rutas completas sobre el mismo cuerpo, para comprobar el contrato de
 * una sola vez y no depender de cómo se serializa un objeto anidado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryApiTests {
    @Autowired
    private MockMvc mvc;

    /** Cuerpo de la consulta indicada, tal y como la vería el navegador. */
    private String body(String path) throws Exception {
        return mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void inventoryReportsStockReservedAndPendingForEveryProduct() throws Exception {
        String body = body("/api/inventory");

        assertThat(JsonPath.<List<?>>read(body, "$.items")).hasSize(10);
        assertThat(JsonPath.<Integer>read(body, "$.totals.stock")).isPositive();
        assertThat(JsonPath.<Integer>read(body, "$.totals.reserved")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.totals.available"))
                .isEqualTo(JsonPath.<Integer>read(body, "$.totals.stock"));
        assertThat(JsonPath.<Integer>read(body, "$.totals.pendingPlacement")).isZero();
        assertThat(JsonPath.<Integer>read(body, "$.totals.occupiedVolume")).isPositive();
        // Ninguna unidad de volumen se pierde ni se duplica: ocupado más libre es la capacidad.
        assertThat(JsonPath.<Integer>read(body, "$.totals.occupiedVolume")
                + JsonPath.<Integer>read(body, "$.totals.availableVolume")).isEqualTo(1000);
        assertThat(((Number) JsonPath.read(body, "$.inventoryRevision")).longValue()).isPositive();
        assertThat(JsonPath.<String>read(body, "$.generatedAt")).isNotBlank();

        assertThat(JsonPath.<String>read(body, "$.items[0].productId")).isEqualTo("GEN-01");
        assertThat(JsonPath.<String>read(body, "$.items[0].productName")).isNotBlank();
        assertThat(JsonPath.<String>read(body, "$.items[0].merchandiseType")).isEqualTo("GENERAL");
        assertThat(JsonPath.<Integer>read(body, "$.items[0].unitVolume")).isPositive();
        assertThat(JsonPath.<Integer>read(body, "$.items[0].stock")).isPositive();
        assertThat(JsonPath.<Integer>read(body, "$.items[0].available"))
                .isEqualTo(JsonPath.<Integer>read(body, "$.items[0].stock"));
        assertThat(JsonPath.<Integer>read(body, "$.items[0].locations")).isPositive();
        assertThat(JsonPath.<String>read(body, "$.items[9].productId")).isEqualTo("ELE-02");
    }

    @Test
    void warehouseReportsHundredLocationsAndTheRealLotDistribution() throws Exception {
        String body = body("/api/warehouse");

        assertThat(JsonPath.<Integer>read(body, "$.warehouse.locationCount")).isEqualTo(100);
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.volumePerLocation")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.occupiedVolume")).isPositive();
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.availableVolume"))
                .isEqualTo(1000 - JsonPath.<Integer>read(body, "$.warehouse.occupiedVolume"));
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.locationsInUse")).isPositive();

        assertThat(JsonPath.<List<?>>read(body, "$.warehouse.locations")).hasSize(100);
        assertThat(JsonPath.<String>read(body, "$.warehouse.locations[0].id")).isEqualTo("A-1-1");
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.locations[0].capacity")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.locations[0].usedVolume")).isNotNegative();
        assertThat(JsonPath.<String>read(body, "$.warehouse.locations[99].id")).isEqualTo("J-5-2");

        // Cada lote declara en qué ubicaciones está repartido. Con la carga inicial, los productos
        // voluminosos no caben en un solo hueco, así que hay al menos un lote repartido.
        assertThat(JsonPath.<List<?>>read(body, "$.warehouse.lots")).hasSize(10);
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.splitLots")).isPositive();
        assertThat(JsonPath.<String>read(body, "$.warehouse.lots[0].id")).startsWith("LOT-");
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.lots[0].units")).isPositive();
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.lots[0].receipts")).isPositive();
        assertThat(JsonPath.<List<?>>read(body, "$.warehouse.lots[0].allocations")).isNotEmpty();
        assertThat(JsonPath.<String>read(body, "$.warehouse.lots[0].allocations[0].locationId")).isNotBlank();
        assertThat(JsonPath.<Integer>read(body, "$.warehouse.lots[0].allocations[0].units")).isPositive();
        assertThat(((Number) JsonPath.read(body, "$.inventoryRevision")).longValue()).isPositive();
    }
}