package com.sopes.backendproyectofinal.simulation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "simulation")
public class SimulationSettings {
    @Min(1) @Max(32)
    private int workerCount = 10;
    @Min(1) @Max(60000)
    private int simulatedSecondsPerRealSecond = 60;
    @Min(1) @Max(3600)
    private int preparationSecondsPerUnit = 60;

    public int getWorkerCount() { return workerCount; }
    public void setWorkerCount(int value) { workerCount = value; }
    public int getSimulatedSecondsPerRealSecond() { return simulatedSecondsPerRealSecond; }
    public void setSimulatedSecondsPerRealSecond(int value) { simulatedSecondsPerRealSecond = value; }
    public int getPreparationSecondsPerUnit() { return preparationSecondsPerUnit; }
    public void setPreparationSecondsPerUnit(int value) { preparationSecondsPerUnit = value; }
}
