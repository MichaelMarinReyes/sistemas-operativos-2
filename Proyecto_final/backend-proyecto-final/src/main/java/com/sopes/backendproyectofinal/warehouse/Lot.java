package com.sopes.backendproyectofinal.warehouse;

import java.util.List;

/**
 * Lote de un producto dentro del almacén, con la lista de ubicaciones donde está repartido.
 *
 * Un lote no exige posiciones contiguas: guarda todas las ubicaciones donde tiene unidades, que es el
 * requisito de asignación no contigua del enunciado. Al ser un registro inmutable y derivado del
 * almacén físico, nunca puede desincronizarse de la realidad: si el almacén dice que quedan cuatro
 * unidades repartidas en tres ubicaciones, el lote dice exactamente eso.
 *
 * El número de orden registra cuántas recepciones se han consolidado en el lote, de modo que se puede
 * seguir el rastro de la mercancía desde que entró hasta que salió.
 */
public record Lot(String id, String productId, int units, List<LotAllocation> allocations, long receipts) {

    public Lot {
        allocations = List.copyOf(allocations);
        if (units < 1) {
            throw new IllegalArgumentException("Un lote debe tener al menos una unidad.");
        }
    }

    /**
     * Crea el lote de un producto a partir de su distribución real.
     * El identificador es estable y legible: el mismo producto siempre muestra el mismo lote.
     */
    public static Lot of(String productId, List<LotAllocation> allocations, long receipts) {
        return new Lot("LOT-" + productId, productId,
                allocations.stream().mapToInt(LotAllocation::units).sum(), allocations, receipts);
    }

    /** Volumen total que ocupa el lote en el almacén. */
    public int usedVolume() {
        return allocations.stream().mapToInt(LotAllocation::usedVolume).sum();
    }

    /** Cuántas ubicaciones distintas ocupa, sean consecutivas o separadas. */
    public int locationCount() {
        return allocations.size();
    }

    /** Indica si el lote está repartido entre varias ubicaciones; no mide su adyacencia. */
    public boolean isSplit() {
        return allocations.size() > 1;
    }
}
