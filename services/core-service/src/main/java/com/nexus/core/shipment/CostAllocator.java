package com.nexus.core.shipment;

import com.nexus.core.money.Money;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Splits one cost into shares in proportion to weights, without losing or creating a single cent.
 *
 * <p>Each share is first rounded down to the currency's smallest unit. The cents left over go one by
 * one to the shares with the largest rounded-off remainder (the "largest remainder" method), so the
 * shares always add up exactly to the total. Example: 10.00 EUR in three equal parts gives
 * 3.34 + 3.33 + 3.33.
 */
public final class CostAllocator {

    private CostAllocator() {
    }

    public static List<Money> allocate(Money total, List<BigDecimal> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("At least one weight is required");
        }
        if (weights.stream().anyMatch(w -> w == null || w.signum() < 0)) {
            throw new IllegalArgumentException("Weights cannot be empty or negative");
        }
        if (total.isNegative()) {
            throw new IllegalArgumentException("Cannot allocate a negative amount");
        }
        String currency = total.currency().getCurrencyCode();
        int scale = total.currency().getDefaultFractionDigits();
        if (total.amount().signum() == 0) {
            return weights.stream().map(w -> Money.zero(currency)).toList();
        }
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.signum() == 0) {
            throw new IllegalArgumentException("Cannot split a cost when every weight is zero");
        }

        BigInteger totalMinor = total.amount().movePointRight(scale).toBigIntegerExact();
        List<BigInteger> shares = new ArrayList<>();
        List<BigDecimal> remainders = new ArrayList<>();
        BigInteger allocated = BigInteger.ZERO;
        for (BigDecimal weight : weights) {
            BigDecimal exact = new BigDecimal(totalMinor).multiply(weight).divide(weightSum, 10, RoundingMode.HALF_EVEN);
            BigInteger floor = exact.setScale(0, RoundingMode.FLOOR).toBigInteger();
            shares.add(floor);
            remainders.add(exact.subtract(new BigDecimal(floor)));
            allocated = allocated.add(floor);
        }

        int leftover = totalMinor.subtract(allocated).intValueExact();
        List<Integer> order = IntStream.range(0, weights.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> remainders.get(i)).reversed()
                        .thenComparing(i -> i))
                .toList();
        for (int k = 0; k < leftover; k++) {
            int index = order.get(k);
            shares.set(index, shares.get(index).add(BigInteger.ONE));
        }
        return shares.stream()
                .map(minor -> Money.of(new BigDecimal(minor).movePointLeft(scale).toPlainString(), currency))
                .toList();
    }
}
