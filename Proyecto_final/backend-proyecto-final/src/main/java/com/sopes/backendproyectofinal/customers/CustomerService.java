package com.sopes.backendproyectofinal.customers;

import com.sopes.backendproyectofinal.shared.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {
    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public Customer create(CreateCustomerRequest request) {
        Customer customer = new Customer(UUID.randomUUID().toString(), request.name().strip(),
                request.serviceLevel(), Instant.now());
        repository.save(customer);
        return customer;
    }

    public List<Customer> findAll() {
        return repository.findAll();
    }

    public Customer findById(String id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "El cliente seleccionado no existe."));
    }
}
