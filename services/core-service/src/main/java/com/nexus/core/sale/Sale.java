package com.nexus.core.sale;

import com.nexus.core.money.Money;
import java.time.LocalDate;
import java.util.List;

/** One selling event, from one location, in one currency, with one or more lines. */
public record Sale(
        Long id,
        String publicId,
        Long customerId,
        String customerName,
        Long locationId,
        LocalDate saleDate,
        String currency,
        String notes,
        List<SaleLine> lines,
        Money total,
        Money profit) {

    public Sale {
        lines = List.copyOf(lines);
    }
}
