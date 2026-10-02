package com.sopes.backendproyectofinal.customers;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public List<Customer> findAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public Customer findById(@PathVariable String id) { return service.findById(id); }

    @PostMapping
    public ResponseEntity<Customer> create(@Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = service.create(request);
        return ResponseEntity.created(URI.create("/api/customers/" + customer.id())).body(customer);
    }
}
