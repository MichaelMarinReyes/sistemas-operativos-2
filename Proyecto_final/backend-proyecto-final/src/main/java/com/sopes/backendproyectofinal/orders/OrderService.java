package com.sopes.backendproyectofinal.orders;

import com.sopes.backendproyectofinal.catalog.CatalogService;
import com.sopes.backendproyectofinal.catalog.Product;
import com.sopes.backendproyectofinal.customers.Customer;
import com.sopes.backendproyectofinal.customers.CustomerService;
import com.sopes.backendproyectofinal.shared.ApiException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderRepository repository;
    private final CustomerService customers;
    private final CatalogService catalog;

    public OrderService(OrderRepository repository, CustomerService customers, CatalogService catalog) {
        this.repository = repository;
        this.customers = customers;
        this.catalog = catalog;
    }

    /** Valida todas las referencias antes de guardar; no deja pedidos parcialmente registrados. */
    public Order create(CreateOrderRequest request) {
        Customer customer = customers.findById(request.customerId());
        Set<String> productIds = new HashSet<>();
        List<OrderLine> lines = request.lines().stream().map(line -> {
            Product product = catalog.findById(line.productId());
            if (!productIds.add(product.id())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "No repitas un producto. Modifica la cantidad de su línea existente.");
            }
            return new OrderLine(product.id(), product.name(), product.merchandiseType(), line.quantity());
        }).toList();
        int totalUnits = lines.stream().mapToInt(OrderLine::quantity).reduce(0, Math::addExact);
        Order order = new Order(UUID.randomUUID().toString(), customer.id(), customer.name(),
                customer.serviceLevel(), lines, totalUnits, OrderStatus.CREATED, Instant.now());
        repository.save(order);
        return order;
    }

    public Order findById(String id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "El pedido solicitado no existe."));
    }

    public OrderPage findAll(OrderStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La página debe ser mayor o igual a cero y el tamaño debe estar entre 1 y 100.");
        }
        List<Order> matching = repository.findAll().stream()
                .filter(order -> status == null || order.status() == status).toList();
        return new OrderPage(matching.stream().skip((long) page * size).limit(size).toList(),
                page, size, matching.size());
    }

    public Summary summarize() {
        List<Order> snapshot = repository.findAll();
        int processing = (int) snapshot.stream().filter(order -> order.status() == OrderStatus.PROCESSING).count();
        int completed = (int) snapshot.stream().filter(order -> order.status() == OrderStatus.COMPLETED).count();
        int failed = (int) snapshot.stream().filter(order -> order.status() == OrderStatus.ERROR).count();
        return new Summary(snapshot.size() - processing - completed - failed, processing, completed, failed);
    }

    public record Summary(int waiting, int processing, int completed, int failed) {}
}
