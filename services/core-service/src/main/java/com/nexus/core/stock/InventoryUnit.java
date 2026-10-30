package com.nexus.core.stock;

import com.nexus.core.money.Money;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One physical item with its own ID, cost and item-level values (for example battery health). */
public record InventoryUnit(
        Long id,
        String publicId,
        Long productId,
        Long locationId,
        UnitStatus status,
        Money unitCost,
        LocalDate receivedDate,
        Map<String, String> attributes) {

    public InventoryUnit {
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
