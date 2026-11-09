package com.nexus.core.report;

import com.nexus.core.money.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * Goods in stock for longer than the owner's limit, the longest first.
 *
 * @param days the owner's limit, from the business settings
 */
public record SlowStock(String currency, int days, List<Row> products, List<MissingRate> missingRates) {

    public SlowStock {
        products = List.copyOf(products);
        missingRates = List.copyOf(missingRates);
    }

    /**
     * @param quantity     pieces older than the limit
     * @param oldest       when the oldest of them arrived
     * @param value        what they cost, at today's rate
     */
    public record Row(long productId, String sku, String name, int quantity, LocalDate oldest, long daysInStock,
                      Money value) {
    }
}
