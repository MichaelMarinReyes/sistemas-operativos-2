package com.sopes.backendproyectofinal.simulation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {
    private final SimulationEngine engine;

    public SimulationController(SimulationEngine engine) { this.engine = engine; }

    @PostMapping("/start")
    public EngineResponse start() { return new EngineResponse(engine.start()); }

    @PostMapping("/stop")
    public EngineResponse stop() { return new EngineResponse(engine.stop()); }

    public record EngineResponse(SimulationEngine.EngineStatus status) {}
}
