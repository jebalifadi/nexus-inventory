package com.nexus.core.stock;

import com.nexus.core.money.Money;
import java.time.LocalDate;

/** A batch of identical goods received together, tracked by quantity. */
public record InventoryLot(
        Long id,
        String publicId,
        Long productId,
        Long locationId,
        int quantityReceived,
        int quantityRemaining,
        Money unitCost,
        LocalDate receivedDate,
        String status) {
}
