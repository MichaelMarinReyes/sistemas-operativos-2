package com.sopes.backendproyectofinal.simulation;

import com.sopes.backendproyectofinal.receipts.ReceiptService;
import org.springframework.stereotype.Service;

/** Serializa el inicio y cierre del conjunto sin retener el bloqueo de pedidos durante las esperas. */
@Service
public class SimulationCoordinator {
    private final SimulationEngine engine;
    private final ReceiptService receipts;
    public SimulationCoordinator(SimulationEngine engine, ReceiptService receipts) {
        this.engine = engine;
        this.receipts = receipts;
    }
    public synchronized SimulationEngine.EngineStatus start() {
        receipts.start();
        try { return engine.start(); }
        catch (RuntimeException exception) {
            receipts.stop();
            throw exception;
        }
    }
    public synchronized SimulationEngine.EngineStatus stop() {
        try { receipts.stop(); }
        finally { engine.stop(); }
        return engine.status();
    }
}
