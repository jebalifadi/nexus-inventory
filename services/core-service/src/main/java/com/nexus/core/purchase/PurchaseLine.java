package com.nexus.core.purchase;

import com.nexus.core.money.Money;
import java.util.List;

/**
 * One line of a purchase: a product, how many, at what price, and where it goes.
 * It lists the stock it created: one unit per piece, or one lot.
 */
public record PurchaseLine(
        Long id,
        int lineNo,
        Long productId,
        Long locationId,
        int quantity,
        Money unitPrice,
        Money lineTotal,
        List<String> unitIds,
        String lotId) {

    public PurchaseLine {
        unitIds = List.copyOf(unitIds);
    }
}
