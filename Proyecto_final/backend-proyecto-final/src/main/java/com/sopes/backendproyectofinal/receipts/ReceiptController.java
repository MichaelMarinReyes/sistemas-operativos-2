package com.sopes.backendproyectofinal.receipts;

import com.sopes.backendproyectofinal.shared.ApiException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {
    private final ReceiptService receipts;
    public ReceiptController(ReceiptService receipts) { this.receipts = receipts; }

    @PostMapping
    public ResponseEntity<ReceiptService.ReceiptView> create(@Valid @RequestBody CreateReceiptRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        String canonical;
        try {
            if (key != null && !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                throw new IllegalArgumentException();
            }
            canonical = key == null ? UUID.randomUUID().toString() : UUID.fromString(key).toString();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La clave de idempotencia debe ser un UUID válido.");
        }
        var receipt = receipts.submit(canonical, request.productId(), request.quantity());
        return ResponseEntity.accepted().location(URI.create("/api/receipts/" + receipt.id())).body(receipt);
    }

    @GetMapping
    public ReceiptService.ReceiptReport list() { return receipts.report(); }

    @GetMapping("/{id}")
    public ReceiptService.ReceiptView find(@PathVariable String id) { return receipts.find(id); }
}
