package com.sopes.backendproyectofinal.shared;

import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;

/** Protege cambios compuestos y permite capturar pedidos y recursos de forma consistente. */
@Component
public class SimulationState {
    public final ReentrantLock lock = new ReentrantLock(true);
}
