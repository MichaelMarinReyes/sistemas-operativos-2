package com.sopes.backendproyectofinal.simulation;

import com.sopes.backendproyectofinal.catalog.MerchandiseType;
import com.sopes.backendproyectofinal.orders.Order;
import com.sopes.backendproyectofinal.orders.OrderStage;
import com.sopes.backendproyectofinal.resources.ResourceType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StagePlanner {
    private final SimulationSettings settings;

    public StagePlanner(SimulationSettings settings) { this.settings = settings; }

    /** Agrupa mercancías del mismo tipo y consolida escaneo/carga después de prepararlas todas. */
    public List<Stage> plan(Order order) {
        Map<MerchandiseType, Integer> groups = new EnumMap<>(MerchandiseType.class);
        order.lines().forEach(line -> groups.merge(line.merchandiseType(), line.quantity(), Integer::sum));
        List<Stage> stages = new ArrayList<>();
        groups.forEach((type, units) -> {
            Map<ResourceType, Integer> required = new EnumMap<>(ResourceType.class);
            required.put(ResourceType.WORKER, type == MerchandiseType.BULKY || type == MerchandiseType.ELECTRONICS ? 2 : 1);
            required.put(ResourceType.PACKING, 1);
            if (type == MerchandiseType.FRAGILE || type == MerchandiseType.ELECTRONICS) required.put(ResourceType.QUALITY, 1);
            if (type == MerchandiseType.HEAVY || type == MerchandiseType.BULKY) required.put(ResourceType.FORKLIFT, 1);
            double factor = switch (type) {
                case GENERAL -> 1;
                case FRAGILE -> 1.5;
                case HEAVY -> 1.25;
                case BULKY -> 1.75;
                case ELECTRONICS -> 2;
            };
            stages.add(new Stage(OrderStage.PREPARING, units * (double) settings.getPreparationSecondsPerUnit() * factor, Map.copyOf(required)));
        });
        stages.add(new Stage(OrderStage.SCANNING, order.totalUnits() * 15.0, Map.of(ResourceType.SCANNER, 1)));
        stages.add(new Stage(OrderStage.LOADING, order.totalUnits() * 30.0,
                Map.of(ResourceType.WORKER, 1, ResourceType.LOADING, 1)));
        return List.copyOf(stages);
    }

    public record Stage(OrderStage stage, double simulatedSeconds, Map<ResourceType, Integer> resources) {}
}
