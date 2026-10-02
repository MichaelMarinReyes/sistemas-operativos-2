package com.sopes.backendproyectofinal.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class StatusController {
    private final StatusService service;

    public StatusController(StatusService service) { this.service = service; }

    @GetMapping
    public StatusResponse getStatus() { return service.getStatus(); }
}
