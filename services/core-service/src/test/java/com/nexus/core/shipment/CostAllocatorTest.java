package com.nexus.core.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.money.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CostAllocatorTest {

    @Test
    void splitsInProportionToValue() {
        List<Money> shares = CostAllocator.allocate(Money.of("60", "EUR"),
                List.of(new BigDecimal("300"), new BigDecimal("100")));

        assertThat(shares).containsExactly(Money.of("45.00", "EUR"), Money.of("15.00", "EUR"));
    }

    @Test
    void neverLosesOrCreatesACent() {
        List<Money> shares = CostAllocator.allocate(Money.of("10.00", "EUR"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        assertThat(shares).containsExactly(
                Money.of("3.34", "EUR"), Money.of("3.33", "EUR"), Money.of("3.33", "EUR"));
    }

    @Test
    void givesLeftoverCentsToTheLargestRemainders() {
        List<Money> shares = CostAllocator.allocate(Money.of("10.00", "EUR"),
                List.of(BigDecimal.ONE, BigDecimal.TWO));

        assertThat(shares).containsExactly(Money.of("3.33", "EUR"), Money.of("6.67", "EUR"));
    }

    @Test
    void usesTheDecimalsOfTheCurrency() {
        List<Money> shares = CostAllocator.allocate(Money.of("1", "TND"),
                List.of(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));

        assertThat(shares).containsExactly(
                Money.of("0.334", "TND"), Money.of("0.333", "TND"), Money.of("0.333", "TND"));
    }

    @Test
    void freeShippingGivesEveryItemZero() {
        assertThat(CostAllocator.allocate(Money.zero("EUR"), List.of(BigDecimal.ZERO, BigDecimal.TEN)))
                .containsExactly(Money.zero("EUR"), Money.zero("EUR"));
    }

    @Test
    void refusesWhenEveryWeightIsZero() {
        assertThatThrownBy(() -> CostAllocator.allocate(Money.of("5", "EUR"), List.of(BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
