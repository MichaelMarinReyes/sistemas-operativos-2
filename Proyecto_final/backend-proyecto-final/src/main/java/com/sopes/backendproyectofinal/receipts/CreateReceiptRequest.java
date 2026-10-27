package com.sopes.backendproyectofinal.receipts;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReceiptRequest(
        @NotBlank(message = "Selecciona un producto.") String productId,
        @NotNull(message = "Indica la cantidad recibida.")
        @Min(value = 1, message = "La cantidad debe ser positiva.")
        @Max(value = 1000000, message = "La cantidad no puede superar un millón de unidades.") Integer quantity) {
}
