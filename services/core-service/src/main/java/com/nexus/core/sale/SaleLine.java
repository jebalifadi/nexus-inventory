package com.nexus.core.sale;

import com.nexus.core.money.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * One line of a sale: either one unit (a phone) or a quantity of a product (6 pairs of sneakers).
 *
 * <p>{@code cost} is the real cost of what was sold, frozen when the sale was saved. {@code cost}
 * and {@code profit} are empty when the amounts are in different currencies, until exchange rates
 * exist.
 */
public record SaleLine(
        Long id,
        int lineNo,
        Long productId,
        String unitId,
        int quantity,
        Money unitPrice,
        Money lineTotal,
        Money cost,
        Money profit,
        List<SaleAllocation> allocations,
        int warrantyDays,
        LocalDate warrantyEndDate,
        String warrantyTerms) {

    public SaleLine {
        allocations = List.copyOf(allocations);
    }
}
