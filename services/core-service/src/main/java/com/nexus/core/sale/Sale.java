package com.nexus.core.sale;

import com.nexus.core.money.Money;
import com.nexus.core.shared.PaymentStatus;
import java.time.LocalDate;
import java.util.List;

/** One selling event, from one location, in one currency, with one or more lines and its payment state. */
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
        Money saleExpenses,
        Money profit,
        String profitUnknownReason,
        Money paid,
        Money balance,
        PaymentStatus paymentStatus) {

    public Sale {
        lines = List.copyOf(lines);
    }
}
