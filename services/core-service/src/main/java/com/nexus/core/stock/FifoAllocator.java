package com.nexus.core.stock;

import java.util.ArrayList;
import java.util.List;

/**
 * First in, first out: takes a quantity from the oldest lots first.
 *
 * <p>Example: lot A holds 4 pairs, lot B holds 10. Selling 6 takes 4 from A and 2 from B.
 */
public final class FifoAllocator {

    /** A lot and how many pieces it still holds. */
    public record LotStock(long lotId, int available) {
    }

    /** How many pieces to take from one lot. */
    public record Take(long lotId, int quantity) {
    }

    private FifoAllocator() {
    }

    /**
     * @param oldestFirst lots sorted from oldest to newest
     * @param quantity    how many pieces are needed
     */
    public static List<Take> allocate(List<LotStock> oldestFirst, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        int available = oldestFirst.stream().mapToInt(LotStock::available).sum();
        if (available < quantity) {
            throw new IllegalArgumentException("Only " + available + " available, " + quantity + " needed");
        }
        List<Take> takes = new ArrayList<>();
        int missing = quantity;
        for (LotStock lot : oldestFirst) {
            if (missing == 0) {
                break;
            }
            int take = Math.min(lot.available(), missing);
            if (take > 0) {
                takes.add(new Take(lot.lotId(), take));
                missing -= take;
            }
        }
        return takes;
    }
}
