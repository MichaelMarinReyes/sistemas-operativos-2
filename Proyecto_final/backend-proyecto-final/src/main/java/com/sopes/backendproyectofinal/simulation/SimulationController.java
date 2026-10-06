package com.sopes.backendproyectofinal.simulation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
    private final SimulationCoordinator coordinator;

    public SimulationController(SimulationCoordinator coordinator) { this.coordinator = coordinator; }

    @PostMapping("/start")
    public EngineResponse start() { return new EngineResponse(coordinator.start()); }

    @PostMapping("/stop")
    public EngineResponse stop() { return new EngineResponse(coordinator.stop()); }

    public record EngineResponse(SimulationEngine.EngineStatus status) {}
}
