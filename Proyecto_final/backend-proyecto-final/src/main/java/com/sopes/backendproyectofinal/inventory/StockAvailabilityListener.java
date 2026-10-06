package com.sopes.backendproyectofinal.inventory;

/**
 * Aviso de que la disponibilidad de existencias cambió y los trabajadores en espera deben volver a
 * evaluarla.
 *
 * Es una notificación, no un acceso compartido: el inventario no lee ni modifica pedidos, solo
 * avisa. Así el módulo de inventario no necesita conocer la estructura interna del planificador.
 */
@FunctionalInterface
public interface StockAvailabilityListener {

    void onAvailabilityChanged();
}
