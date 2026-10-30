package com.nexus.core.stock;

import java.util.Map;
import java.util.Set;

/** Life cycle of one individually tracked item, with the allowed next steps. */
public enum UnitStatus {
    PURCHASED,
    IN_TRANSIT,
    IN_INVENTORY,
    LISTED,
    RESERVED,
    SOLD,
    CLOSED,
    RETURNED,
    CANCELLED;

    private static final Map<UnitStatus, Set<UnitStatus>> ALLOWED = Map.of(
            PURCHASED, Set.of(IN_TRANSIT, IN_INVENTORY, CANCELLED),
            IN_TRANSIT, Set.of(IN_INVENTORY, CANCELLED),
            IN_INVENTORY, Set.of(LISTED, RESERVED, SOLD, IN_TRANSIT, CANCELLED),
            LISTED, Set.of(IN_INVENTORY, RESERVED, SOLD),
            RESERVED, Set.of(LISTED, IN_INVENTORY, SOLD),
            SOLD, Set.of(CLOSED, RETURNED),
            CLOSED, Set.of(RETURNED),
            RETURNED, Set.of(IN_INVENTORY),
            CANCELLED, Set.of());

    public boolean canMoveTo(UnitStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    /** Statuses in which the item is physically available in stock. */
    public boolean isInStock() {
        return this == IN_INVENTORY || this == LISTED || this == RESERVED;
    }
}
