package com.sopes.backendproyectofinal.simulation;

import org.springframework.stereotype.Component;

/** Reloj monotónico: detener el motor congela el tiempo; reanudar conserva lo acumulado. */
@Component
public class SimulationClock {
    private final SimulationSettings settings;
    private long accumulatedNanos;
    private long startedAt;
    private boolean running;

    public SimulationClock(SimulationSettings settings) { this.settings = settings; }

    public synchronized void start() {
        if (!running) { startedAt = System.nanoTime(); running = true; }
    }

    public synchronized void stop() {
        if (running) { accumulatedNanos += System.nanoTime() - startedAt; running = false; }
    }

    public synchronized double elapsedMinutes() {
        long elapsed = accumulatedNanos + (running ? System.nanoTime() - startedAt : 0);
        return elapsed / 1_000_000_000.0 * settings.getSimulatedSecondsPerRealSecond() / 60;
    }

    public long realNanos(double simulatedSeconds) {
        return Math.max(1, (long) (simulatedSeconds / settings.getSimulatedSecondsPerRealSecond() * 1_000_000_000));
    }
}
