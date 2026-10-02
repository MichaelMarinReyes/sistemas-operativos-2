package com.sopes.backendproyectofinal.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "Selecciona un cliente.") String customerId,
        @NotEmpty(message = "Agrega al menos un producto al pedido.")
        @Size(max = 100, message = "El pedido admite como máximo 100 líneas.")
        List<@NotNull(message = "La línea del pedido no puede estar vacía.") @Valid Line> lines) {

    public record Line(
            @NotBlank(message = "Selecciona un producto.") String productId,
            @NotNull(message = "Indica la cantidad de unidades.")
            @Min(value = 1, message = "La cantidad debe ser un entero mayor que cero.")
            @Max(value = 1000000, message = "La cantidad máxima por producto es 1000000.") Integer quantity) {}
}
