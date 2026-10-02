package com.sopes.backendproyectofinal.catalog;

import com.sopes.backendproyectofinal.shared.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Catálogo fijo del simulador; los nombres son textos de presentación en español. */
@Service
public class CatalogService {
    private final List<Product> products = List.of(
            new Product("GEN-01", "Cuaderno", MerchandiseType.GENERAL, 1),
            new Product("GEN-02", "Camiseta", MerchandiseType.GENERAL, 1),
            new Product("FRA-01", "Vaso de vidrio", MerchandiseType.FRAGILE, 2),
            new Product("FRA-02", "Florero", MerchandiseType.FRAGILE, 3),
            new Product("PES-01", "Disco de pesas", MerchandiseType.HEAVY, 3),
            new Product("PES-02", "Caja de herramientas", MerchandiseType.HEAVY, 4),
            new Product("VOL-01", "Silla", MerchandiseType.BULKY, 8),
            new Product("VOL-02", "Caja organizadora", MerchandiseType.BULKY, 6),
            new Product("ELE-01", "Teléfono", MerchandiseType.ELECTRONICS, 1),
            new Product("ELE-02", "Computadora portátil", MerchandiseType.ELECTRONICS, 3));

    public List<Product> findAll() { return products; }

    public Product findById(String id) {
        return products.stream().filter(product -> product.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Uno de los productos seleccionados no existe en el catálogo."));
    }
}
