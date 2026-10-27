package com.sopes.backendproyectofinal.shared;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Protege cambios compuestos y permite capturar pedidos y recursos de forma consistente.
 */
@Component
public class SimulationState {
    public final ReentrantLock lock = new ReentrantLock(true);
}
