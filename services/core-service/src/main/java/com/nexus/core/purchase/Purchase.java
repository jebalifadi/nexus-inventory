package com.nexus.core.purchase;

import com.nexus.core.money.Money;
import java.time.LocalDate;
import java.util.List;

/** One buying event from one supplier, in one currency, with one or more lines. */
public record Purchase(
        Long id,
        String publicId,
        Long supplierId,
        String supplierName,
        LocalDate purchaseDate,
        String currency,
        String notes,
        List<PurchaseLine> lines,
        Money total) {

    public Purchase {
        lines = List.copyOf(lines);
    }
}
