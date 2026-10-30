package com.nexus.core.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsAmountsInTheSameCurrency() {
        Money total = Money.of("10.00", "EUR").plus(Money.of("5.00", "EUR"));

        assertThat(total).isEqualTo(Money.of("15.00", "EUR"));
    }

    @Test
    void addsDecimalsExactly() {
        Money total = Money.of("0.10", "EUR").plus(Money.of("0.20", "EUR"));

        assertThat(total).isEqualTo(Money.of("0.30", "EUR"));
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        Money euros = Money.of("10", "EUR");
        Money dinars = Money.of("5", "TND");

        assertThatThrownBy(() -> euros.plus(dinars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("convert to one currency first");
    }

    @Test
    void usesTheDecimalsOfEachCurrency() {
        assertThat(Money.of("1.5", "TND")).hasToString("1.500 TND");
        assertThat(Money.of("1.5", "EUR")).hasToString("1.50 EUR");
    }
    @Test
    void subtractsAmountsInTheSameCurrency() {
        Money rest = Money.of("10.00", "EUR").minus(Money.of("3.00", "EUR"));

        assertThat(rest).isEqualTo(Money.of("7.00", "EUR"));
    }
}
