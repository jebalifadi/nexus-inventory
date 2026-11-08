package com.nexus.core.writeoff;

import com.nexus.core.money.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * Goods that left stock without being sold (damaged, lost, stolen).
 *
 * @param cost the real cost lost: purchase, shipping and repairs; one entry per currency
 */
public record WriteOff(
        String publicId,
        String unitId,
        String lotId,
        long productId,
        int quantity,
        String reason,
        LocalDate date,
        List<Money> cost) {

    public WriteOff {
        cost = List.copyOf(cost);
    }
}
