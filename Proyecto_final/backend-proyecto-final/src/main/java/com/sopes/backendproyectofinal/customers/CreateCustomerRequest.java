package com.sopes.backendproyectofinal.customers;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio.")
        @Size(max = 100, message = "El nombre debe tener como máximo 100 caracteres.") String name,
        @NotNull(message = "Selecciona un nivel de servicio.")
        @Min(value = 1, message = "El nivel de servicio debe estar entre 1 y 5.")
        @Max(value = 5, message = "El nivel de servicio debe estar entre 1 y 5.") Integer serviceLevel) {}
