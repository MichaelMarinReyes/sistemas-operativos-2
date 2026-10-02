package com.sopes.backendproyectofinal.customers;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** Almacenamiento en memoria; las cuentas se reinician con el servidor. */
@Repository
public class CustomerRepository {
    private final ConcurrentHashMap<String, Customer> customers = new ConcurrentHashMap<>();

    public void save(Customer customer) {
        customers.put(customer.id(), customer);
    }

    public Optional<Customer> findById(String id) {
        return Optional.ofNullable(customers.get(id));
    }

    public List<Customer> findAll() {
        return customers.values().stream()
                .sorted(Comparator.comparing(Customer::createdAt).thenComparing(Customer::id)).toList();
    }
}
