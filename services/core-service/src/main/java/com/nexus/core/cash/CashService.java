package com.nexus.core.cash;

import com.nexus.core.money.Money;
import com.nexus.core.payment.OpenBalance;
import com.nexus.core.payment.PaymentService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.stock.StockService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of cash and capital: how much money the business has, what the owner put in or took
 * out, and what the whole business is worth.
 */
@Service
public class CashService {

    private final CashRepository repository;
    private final StockService stock;
    private final PaymentService payments;

    CashService(CashRepository repository, StockService stock, PaymentService payments) {
        this.repository = repository;
        this.stock = stock;
        this.payments = payments;
    }

    /** Records money the owner puts in or takes out. Taking out more cash than there is, is refused. */
    @Transactional
    public CapitalMovement recordCapital(CapitalType type, Money amount, LocalDate date, String notes) {
        if (type == null) {
            throw new BusinessRuleException("Type is required");
        }
        requirePositive(amount);
        repository.lockCash();
        if (type.takesCashOut()) {
            requireEnoughCash(amount);
        }
        long id = repository.insertCapital(type, amount.amount(), code(amount), dateOrToday(date), blank(notes));
        return repository.findCapital(id).orElseThrow();
    }

    /** Records changing money: what was given and what was received, at the rate actually obtained. */
    @Transactional
    public CurrencyExchange exchange(Money from, Money to, LocalDate date, String notes) {
        requirePositive(from);
        requirePositive(to);
        if (from.currency().equals(to.currency())) {
            throw new BusinessRuleException("An exchange needs two different currencies");
        }
        repository.lockCash();
        requireEnoughCash(from);
        long id = repository.insertExchange(from.amount(), code(from), to.amount(), code(to), dateOrToday(date),
                blank(notes));
        return repository.findExchange(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Money cash(String currency) {
        return Money.of(repository.cashByCurrency().getOrDefault(currency, BigDecimal.ZERO).toPlainString(), currency);
    }

    /** The cash book: every movement of money, oldest first. */
    @Transactional(readOnly = true)
    public List<CashMovement> cashBook() {
        return repository.movements();
    }

    @Transactional(readOnly = true)
    public List<CapitalSummary> capital() {
        List<CapitalSummary> result = new ArrayList<>();
        repository.capitalTotals().forEach((currency, totals) -> {
            Money added = money(totals.get(CapitalType.OPENING_BALANCE), currency)
                    .plus(money(totals.get(CapitalType.CAPITAL_ADDED), currency));
            Money withdrawn = money(totals.get(CapitalType.CAPITAL_WITHDRAWN), currency);
            result.add(new CapitalSummary(currency, added, withdrawn, added.minus(withdrawn),
                    money(totals.get(CapitalType.PROFIT_WITHDRAWN), currency)));
        });
        return result;
    }

    /** The whole business, one line per currency. No conversion: each currency is shown as it is. */
    @Transactional(readOnly = true)
    public List<CurrencyPosition> overview() {
        Map<String, BigDecimal> cash = repository.cashByCurrency();
        Map<String, BigDecimal> stockValue = stock.stockValueByCurrency();
        Map<String, BigDecimal> receivables = sumByCurrency(payments.receivables());
        Map<String, BigDecimal> payables = sumByCurrency(payments.payables());

        TreeSet<String> currencies = new TreeSet<>();
        currencies.addAll(cash.keySet());
        currencies.addAll(stockValue.keySet());
        currencies.addAll(receivables.keySet());
        currencies.addAll(payables.keySet());

        List<CurrencyPosition> positions = new ArrayList<>();
        for (String currency : currencies) {
            Money c = money(cash.get(currency), currency);
            Money s = money(stockValue.get(currency), currency);
            Money r = money(receivables.get(currency), currency);
            Money p = money(payables.get(currency), currency);
            positions.add(new CurrencyPosition(currency, c, s, r, p, c.plus(s).plus(r).minus(p)));
        }
        return positions;
    }

    private void requireEnoughCash(Money amount) {
        Money available = cash(code(amount));
        if (amount.amount().compareTo(available.amount()) > 0) {
            throw new BusinessRuleException("Only " + available + " in cash, " + amount + " needed");
        }
    }

    private static Map<String, BigDecimal> sumByCurrency(List<OpenBalance> balances) {
        Map<String, BigDecimal> totals = new TreeMap<>();
        for (OpenBalance balance : balances) {
            totals.merge(code(balance.balance()), balance.balance().amount(), BigDecimal::add);
        }
        return totals;
    }

    private static void requirePositive(Money amount) {
        if (amount == null || amount.amount().signum() <= 0) {
            throw new BusinessRuleException("Amount must be more than zero");
        }
    }

    private static Money money(BigDecimal amount, String currency) {
        return Money.of(amount == null ? "0" : amount.toPlainString(), currency);
    }

    private static String code(Money money) {
        return money.currency().getCurrencyCode();
    }

    private static LocalDate dateOrToday(LocalDate date) {
        return date == null ? LocalDate.now() : date;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
