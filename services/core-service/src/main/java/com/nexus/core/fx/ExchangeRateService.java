package com.nexus.core.fx;

import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchange rates by day, and conversion of money with the rate that was valid on a given date.
 *
 * <p>Nexus never guesses a rate: when none is known for a date, the conversion is empty and the
 * screens show the value as unknown, with the reason.
 */
@Service
public class ExchangeRateService {

    private final ExchangeRateRepository repository;

    ExchangeRateService(ExchangeRateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ExchangeRate addRate(String base, String quote, BigDecimal rate, LocalDate rateDate) {
        String from = requireCurrency(base);
        String to = requireCurrency(quote);
        if (from.equals(to)) {
            throw new BusinessRuleException("A rate needs two different currencies");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new BusinessRuleException("Rate must be more than zero");
        }
        long id = repository.insert(from, to, rate, rateDate == null ? LocalDate.now() : rateDate, "MANUAL");
        return repository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ExchangeRate> listRates() {
        return repository.findAll();
    }

    /**
     * The rate from one currency to another valid on a date. Uses the direct rate (EUR→TND) or the
     * inverse one (TND→EUR), whichever is more recent.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> rateOn(String from, String to, LocalDate date) {
        if (from.equals(to)) {
            return Optional.of(BigDecimal.ONE);
        }
        Optional<ExchangeRate> direct = repository.findValidOn(from, to, date);
        Optional<ExchangeRate> inverse = repository.findValidOn(to, from, date);
        if (direct.isPresent() && (inverse.isEmpty()
                || !direct.get().rateDate().isBefore(inverse.get().rateDate()))) {
            return Optional.of(direct.get().rate());
        }
        return inverse.map(r -> BigDecimal.ONE.divide(r.rate(), MathContext.DECIMAL64));
    }

    /** Converts money into another currency at the rate valid on that date, or empty if no rate is known. */
    @Transactional(readOnly = true)
    public Optional<Money> convert(Money amount, String targetCurrency, LocalDate date) {
        String from = amount.currency().getCurrencyCode();
        if (from.equals(targetCurrency)) {
            return Optional.of(amount);
        }
        return rateOn(from, targetCurrency, date).map(rate -> amount.convertTo(targetCurrency, rate));
    }

    private static String requireCurrency(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("Currency is required");
        }
        try {
            return Currency.getInstance(code.strip().toUpperCase()).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown currency: " + code);
        }
    }
}
