package com.sopes.backendproyectofinal.customers;

import java.time.Instant;

/** Cuenta del simulador; su identificador permite asociar los pedidos al cliente. */
public record Customer(String id, String name, int serviceLevel, Instant createdAt) {}
