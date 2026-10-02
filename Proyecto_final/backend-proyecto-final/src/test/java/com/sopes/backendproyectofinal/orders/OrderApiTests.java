package com.sopes.backendproyectofinal.orders;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verifica el flujo HTTP completo y que las solicitudes inválidas no creen pedidos parciales. */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiTests {
    @Autowired private MockMvc mvc;

    private String createCustomer(int level) throws Exception {
        String response = mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  María López  \",\"serviceLevel\":" + level + "}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/customers/")))
                .andExpect(jsonPath("$.name").value("María López"))
                .andExpect(jsonPath("$.serviceLevel").value(level))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String orderBody(String customerId, String lines) {
        return "{\"customerId\":\"" + customerId + "\",\"lines\":" + lines + "}";
    }

    private int orderCount() throws Exception {
        String response = mvc.perform(get("/api/orders")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.total");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void createsAccountAndMultiProductOrderForEveryServiceLevel(int level) throws Exception {
        String customerId = createCustomer(level);
        int previousCount = orderCount();
        String response = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(customerId, """
                                [{"productId":"GEN-01","quantity":2},{"productId":"FRA-01","quantity":3}]
                                """)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/orders/")))
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.serviceLevel").value(level))
                .andExpect(jsonPath("$.totalUnits").value(5))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.lines[1].merchandiseType").value("FRAGILE"))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(response, "$.id");
        mvc.perform(get("/api/orders/" + orderId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").value(5));
        mvc.perform(get("/api/customers/" + customerId)).andExpect(status().isOk());
        assertEquals(previousCount + 1, orderCount());
        mvc.perform(get("/api/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.waiting").value(orderCount()))
                .andExpect(jsonPath("$.resources", hasSize(6)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"   \",\"serviceLevel\":3}",
            "{\"name\":\"Ana\",\"serviceLevel\":0}",
            "{\"name\":\"Ana\",\"serviceLevel\":6}",
            "{\"name\":\"Ana\"}",
            "{\"name\":\"Ana\",\"serviceLevel\":1.5}"
    })
    void rejectsInvalidAccounts(String body) throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Solicitud inválida"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]", "null", "[null]",
            "[{\"productId\":\"GEN-01\",\"quantity\":0}]",
            "[{\"productId\":\"GEN-01\",\"quantity\":-1}]",
            "[{\"productId\":\"GEN-01\",\"quantity\":1.5}]",
            "[{\"productId\":\"GEN-01\",\"quantity\":1000001}]",
            "[{\"productId\":\"GEN-01\"}]",
            "[{\"productId\":\"\",\"quantity\":1}]",
            "[{\"productId\":\"GEN-01\",\"quantity\":1},{\"productId\":\"GEN-01\",\"quantity\":2}]"
    })
    void rejectsInvalidLinesWithoutSaving(String lines) throws Exception {
        String customerId = createCustomer(3);
        int previousCount = orderCount();
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(customerId, lines)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida"));
        assertEquals(previousCount, orderCount());
    }

    @Test
    void rejectsMissingReferencesAtomically() throws Exception {
        int previousCount = orderCount();
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("missing", "[{\"productId\":\"GEN-01\",\"quantity\":1}]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("El cliente seleccionado no existe."));
        String customerId = createCustomer(2);
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(customerId, """
                                [{"productId":"GEN-01","quantity":1},{"productId":"missing","quantity":1}]
                                """)))
                .andExpect(status().isNotFound());
        assertEquals(previousCount, orderCount());
    }

    @Test
    void listsCatalogAndValidatesPaginationAndStatus() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(10)));
        mvc.perform(get("/api/orders?size=1&status=CREATED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1));
        mvc.perform(get("/api/orders?status=COMPLETED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/orders?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders?status=INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/missing")).andExpect(status().isNotFound());
    }
}
