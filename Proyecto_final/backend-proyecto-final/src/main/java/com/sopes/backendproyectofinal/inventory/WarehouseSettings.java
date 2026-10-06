package com.sopes.backendproyectofinal.inventory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;

/**
 * Configuración del almacén y de la carga inicial de mercancía.
 *
 * Las cantidades iniciales se pueden ajustar con propiedades con el prefijo
 * warehouse.initial-stock, por ejemplo warehouse.initial-stock.GEN-01=50.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "warehouse")
public class WarehouseSettings {

    @Min(1)
    private int locationCapacity = 10;

    @Min(1)
    private int aisles = 10;

    @Min(1)
    private int shelvesPerAisle = 5;

    @Min(1)
    private int levelsPerShelf = 2;

    private java.util.Map<String, Integer> initialStock = defaultInitialStock();

    /**
     * Carga inicial de referencia. El total de volumen debe caber en el almacén para que ningún
     * producto arranque pendiente de espacio.
     */
    private static java.util.Map<String, Integer> defaultInitialStock() {
        java.util.Map<String, Integer> stock = new java.util.LinkedHashMap<>();
        stock.put("GEN-01", 40);
        stock.put("GEN-02", 40);
        stock.put("FRA-01", 30);
        stock.put("FRA-02", 30);
        stock.put("PES-01", 25);
        stock.put("PES-02", 20);
        stock.put("VOL-01", 10);
        stock.put("VOL-02", 15);
        stock.put("ELE-01", 30);
        stock.put("ELE-02", 25);
        return stock;
    }

    public int locationCapacity() { return locationCapacity; }

    public void setLocationCapacity(int value) { this.locationCapacity = value; }

    public int aisles() { return aisles; }

    public void setAisles(int value) { this.aisles = value; }

    public int shelvesPerAisle() { return shelvesPerAisle; }

    public void setShelvesPerAisle(int value) { this.shelvesPerAisle = value; }

    public int levelsPerShelf() { return levelsPerShelf; }

    public void setLevelsPerShelf(int value) { this.levelsPerShelf = value; }

    public int locationCount() { return aisles * shelvesPerAisle * levelsPerShelf; }

    public java.util.Map<String, Integer> initialStock() {
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(initialStock));
    }

    public void setInitialStock(java.util.Map<String, Integer> value) { this.initialStock = value; }
}
