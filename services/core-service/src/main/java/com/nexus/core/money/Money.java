package com.nexus.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An exact amount of money in one currency.
 *
 * <p>Amounts use {@link BigDecimal} and are always rounded to the currency's number of decimals
 * (EUR 2, TND 3). Amounts in different currencies can never be combined directly; they must be
 * converted first.
 */
public record Money(BigDecimal amount, Currency currency) {

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        amount = amount.setScale(currency.getDefaultFractionDigits(), ROUNDING);
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return of("0", currencyCode);
    }

    public Money plus(Money other) {
        requireSameCurrency(other, "add");
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other, "subtract");
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    private void requireSameCurrency(Money other, String operation) {
        Objects.requireNonNull(other, "other amount is required");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot " + operation + " " + currency.getCurrencyCode()
                    + " and " + other.currency.getCurrencyCode() + ": convert to one currency first");
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
