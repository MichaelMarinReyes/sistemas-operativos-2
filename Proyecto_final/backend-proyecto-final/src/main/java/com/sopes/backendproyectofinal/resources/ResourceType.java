package com.sopes.backendproyectofinal.resources;

/** El orden de declaración es el orden global de adquisición de recursos. */
public enum ResourceType {
    WORKER("Encargados de bodega", 10),
    FORKLIFT("Montacargas", 3),
    PACKING("Estaciones de empaque", 6),
    QUALITY("Control de calidad", 4),
    SCANNER("Escáner compartido", 1),
    LOADING("Áreas de carga", 2);

    private final String label;
    private final int capacity;

    ResourceType(String label, int capacity) { this.label = label; this.capacity = capacity; }
    public String label() { return label; }
    public int capacity() { return capacity; }
}
