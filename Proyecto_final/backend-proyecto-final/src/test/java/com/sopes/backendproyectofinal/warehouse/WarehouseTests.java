package com.sopes.backendproyectofinal.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sopes.backendproyectofinal.catalog.CatalogService;
import com.sopes.backendproyectofinal.catalog.Product;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Comprueba el algoritmo de colocación del almacén con una sola hebra.
 *
 * Aquí se demuestra que la asignación es correcta por sí sola: que no excede capacidades, que
 * respeta el volumen de cada unidad y que reparte de forma determinista. Las pruebas que importan
 * para el análisis de concurrencia son las de reserva simultánea, que están en el módulo de
 * inventario; estas verifican el algoritmo físico usado por ese módulo.
 */
class WarehouseTests {
    private static final CatalogService CATALOG = new CatalogService();

    /** Producto del catálogo por identificador, para no repetir el volumen en cada prueba. */
    private static Product product(String id) {
        return CATALOG.findById(id);
    }

    @Test
    void startsWithOneHundredEmptyLocationsOfTenUnits() {
        Warehouse warehouse = new Warehouse(10, 10, 5, 2);

        assertThat(warehouse.locationCount()).isEqualTo(100);
        assertThat(warehouse.totalCapacity()).isEqualTo(1000);
        assertThat(warehouse.occupiedVolume()).isZero();
        assertThat(warehouse.availableVolume()).isEqualTo(1000);
        assertThat(warehouse.locationsInUse()).isZero();
        assertThat(warehouse.locations().get(0).id()).isEqualTo("A-1-1");
        assertThat(warehouse.locations().get(99).id()).isEqualTo("J-5-2");
        assertThatCode(() -> WarehouseInvariants.checkVolume(warehouse, warehouse.locations()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDimensionsThatDoNotMakeSense() {
        assertThatThrownBy(() -> new Warehouse(0, 10, 5, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Warehouse(10, 0, 5, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Solo se colocan unidades enteras: una ubicación con 2 libres no acepta una unidad de 3. */
    @Test
    void onlyStoresUnitsThatFitWhole() {
        Warehouse warehouse = new Warehouse(10, 1, 2, 1);
        Product unit = product("GEN-01");
        assertThat(warehouse.store(unit, 5)).hasSize(1);
        assertThat(warehouse.occupiedVolume()).isEqualTo(5);

        Product bulky = product("VOL-01");
        assertThat(warehouse.store(bulky, 1)).hasSize(1);
        assertThat(warehouse.occupiedVolume()).isEqualTo(13);
        assertThat(warehouse.availableVolume()).isEqualTo(7);
        assertThatCode(() -> WarehouseInvariants.checkVolume(warehouse, warehouse.locations()))
                .doesNotThrowAnyException();
    }

    /** Cuando no cabe ninguna unidad entera, el almacén queda intacto y no se pierde mercancía. */
    @Test
    void keepsWarehouseUntouchedWhenNothingFits() {
        Warehouse warehouse = new Warehouse(10, 1, 1, 1);
        warehouse.store(product("VOL-01"), 1);

        assertThat(warehouse.store(product("VOL-01"), 1)).isEmpty();
        assertThat(warehouse.occupiedVolume()).isEqualTo(8);
        assertThat(warehouse.store(product("GEN-01"), 0)).isEmpty();
    }

    /**
     * Consolidación. Un segundo ingreso del mismo producto se coloca donde ya hay unidades del
     * mismo tipo en lugar de abrir una ubicación nueva, para no fragmentar el almacén.
     */
    @Test
    void consolidatesRepeatedReceiptsOfTheSameProduct() {
        Warehouse warehouse = new Warehouse(10, 10, 5, 2);
        warehouse.store(product("GEN-01"), 3);
        int locationsAfterFirst = warehouse.locationsInUse();

        warehouse.store(product("GEN-01"), 4);

        assertThat(warehouse.locationsInUse()).isEqualTo(locationsAfterFirst);
        assertThat(warehouse.unitsAt("A-1-1", "GEN-01")).isEqualTo(7);
        assertThat(warehouse.occupiedVolume()).isEqualTo(7);
    }

    /** La misma secuencia de ingresos produce siempre la misma distribución física. */
    @Test
    void placementIsReproducible() {
        Warehouse first = new Warehouse(10, 10, 5, 2);
        Warehouse second = new Warehouse(10, 10, 5, 2);
        List<String> products = List.of("FRA-02", "PES-01", "VOL-01", "GEN-01", "ELE-02", "VOL-02");

        products.forEach(id -> {
            first.store(product(id), 4);
            second.store(product(id), 4);
        });

        assertThat(first.unitsByProduct()).isEqualTo(second.unitsByProduct());
        assertThat(first.occupiedVolume()).isEqualTo(second.occupiedVolume());
    }

    /** Retirar unidades libera el volumen exacto que ocupaban. */
    @Test
    void withdrawFreesExactlyTheUsedVolume() {
        Warehouse warehouse = new Warehouse(10, 10, 5, 2);
        warehouse.store(product("ELE-02"), 2);
        assertThat(warehouse.occupiedVolume()).isEqualTo(6);

        warehouse.withdraw("A-1-1", "ELE-02", 1, 3);

        assertThat(warehouse.occupiedVolume()).isEqualTo(3);
        assertThat(warehouse.unitsAt("A-1-1", "ELE-02")).isEqualTo(1);
        assertThat(warehouse.unitsByProduct().get("ELE-02")).containsEntry("A-1-1", 1);
    }

    /** Retirar todas las unidades borra el producto de esa ubicación y libera su volumen entero. */
    @Test
    void withdrawRemovesTheProductWhenNothingIsLeft() {
        Warehouse warehouse = new Warehouse(10, 10, 5, 2);
        warehouse.store(product("ELE-02"), 2);
        warehouse.withdraw("A-1-1", "ELE-02", 2, 3);

        assertThat(warehouse.occupiedVolume()).isZero();
        assertThat(warehouse.unitsAt("A-1-1", "ELE-02")).isZero();
        assertThat(warehouse.unitsByProduct()).isEmpty();
    }

    /** Retirar más de lo que hay no inventa existencias ni deja volúmenes negativos. */
    @Test
    void withdrawNeverCreatesPhantomUnits() {
        Warehouse warehouse = new Warehouse(10, 10, 5, 2);
        warehouse.store(product("GEN-01"), 3);

        assertThatThrownBy(() -> warehouse.withdraw("A-1-1", "GEN-01", 10, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(warehouse.occupiedVolume()).isEqualTo(3);
        warehouse.withdraw("A-1-1", "GEN-01", 3, 1);

        assertThat(warehouse.occupiedVolume()).isZero();
        assertThat(warehouse.unitsByProduct()).isEmpty();
        assertThat(warehouse.locationsInUse()).isZero();
    }

    /** Devolver unidades a su ubicación de origen las devuelve a donde estaban. */
    @Test
    void storeAtReturnsUnitsToTheOriginLocation() {
        Warehouse warehouse = new Warehouse(10, 10, 5, 2);
        warehouse.store(product("GEN-01"), 4);
        warehouse.withdraw("A-1-1", "GEN-01", 4, 1);

        assertThat(warehouse.storeAt(product("GEN-01"), "A-1-1", 4)).isEqualTo(4);
        assertThat(warehouse.unitsAt("A-1-1", "GEN-01")).isEqualTo(4);

        // Una ubicación llena no admite ni una unidad más: devuelve cero y no inventa espacio.
        assertThat(warehouse.storeAt(product("GEN-01"), "A-1-1", 6)).isEqualTo(6);
        assertThat(warehouse.storeAt(product("GEN-01"), "A-1-1", 1)).isZero();
        assertThat(warehouse.unitsAt("A-1-1", "GEN-01")).isEqualTo(10);
    }

    /** El almacén rechaza una ubicación que no existe en lugar de aceptarla en silencio. */
    @Test
    void storeAtIgnoresUnknownLocations() {
        Warehouse warehouse = new Warehouse(10, 10, 5, 2);

        assertThat(warehouse.storeAt(product("GEN-01"), "Z-9-9", 1)).isZero();
        assertThat(warehouse.occupiedVolume()).isZero();
    }

    /** Los lotes derivados del almacén cuadran con las existencias declaradas. */
    @Test
    void derivedLotsMatchTheStoredDetail() {
        Warehouse warehouse = new Warehouse(10, 10, 5, 2);
        warehouse.store(product("ELE-02"), 7);

        Map<String, Map<String, Integer>> detail = warehouse.unitsByProduct();
        List<LotAllocation> allocations = detail.get("ELE-02").entrySet().stream()
                .map(entry -> new LotAllocation(entry.getKey(), entry.getValue(), entry.getValue() * 3))
                .toList();
        Lot lot = Lot.of("ELE-02", allocations, 1);

        assertThatCode(() -> WarehouseInvariants.checkLots(List.of(lot))).doesNotThrowAnyException();
        assertThatCode(() -> WarehouseInvariants.checkStoredUnits(warehouse, Map.of(
                "ELE-02", product("ELE-02")))).doesNotThrowAnyException();
        assertThat(lot.units()).isEqualTo(7);
        assertThat(lot.usedVolume()).isEqualTo(warehouse.occupiedVolume());
        assertThat(lot.isSplit()).isTrue();
    }
}
