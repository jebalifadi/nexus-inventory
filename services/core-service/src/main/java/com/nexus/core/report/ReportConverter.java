package com.nexus.core.report;

import com.nexus.core.fx.ExchangeRateService;
import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/** Converts amounts into one currency for one report, and remembers which rates were missing. */
final class ReportConverter {

    private final ExchangeRateService rates;
    private final String target;
    private final Map<String, LocalDate> missing = new TreeMap<>();

    ReportConverter(ExchangeRateService rates, String currency) {
        this.rates = rates;
        this.target = requireCurrency(currency);
    }

    String target() {
        return target;
    }

    Money zero() {
        return Money.zero(target);
    }

    Optional<Money> convert(Money amount, LocalDate day) {
        Optional<Money> converted = rates.convert(amount, target, day);
        if (converted.isEmpty()) {
            // A rate covers its own date and every later one, so the earliest day is the one to name.
            missing.merge(amount.currency().getCurrencyCode(), day, (a, b) -> a.isBefore(b) ? a : b);
        }
        return converted;
    }

    /** The amount converted, or zero when no rate is known: it is left out, and reported as missing. */
    Money convertOrZero(Money amount, LocalDate day) {
        return convert(amount, day).orElse(zero());
    }

    <T> Money total(List<T> items, Function<T, Money> amount, Function<T, LocalDate> day) {
        Money total = zero();
        for (T item : items) {
            total = total.plus(convertOrZero(amount.apply(item), day.apply(item)));
        }
        return total;
    }

    List<MissingRate> missing() {
        return missing.entrySet().stream()
                .map(entry -> new MissingRate(entry.getKey(), target, entry.getValue()))
                .toList();
    }

    private static String requireCurrency(String code) {
        try {
            return Currency.getInstance(code.strip().toUpperCase()).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown currency: " + code);
        }
    }
}
