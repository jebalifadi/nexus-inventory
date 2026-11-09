package com.nexus.core.report;

import com.nexus.core.expense.Expense;
import com.nexus.core.expense.ExpenseService;
import com.nexus.core.fx.ExchangeRateService;
import com.nexus.core.money.Money;
import com.nexus.core.payment.OpenBalance;
import com.nexus.core.payment.PaymentService;
import com.nexus.core.purchase.Purchase;
import com.nexus.core.purchase.PurchaseService;
import com.nexus.core.report.ReportPosition.OpenBalances;
import com.nexus.core.report.ReportPosition.Row;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shipment.Shipment;
import com.nexus.core.shipment.ShipmentService;
import com.nexus.core.stock.StockService;
import com.nexus.core.writeoff.WriteOff;
import com.nexus.core.writeoff.WriteOffService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place that works out the business figures, so every screen shows the same numbers.
 *
 * <p>It reads through the other modules' services, so a sale's profit or a cancelled write-off means
 * exactly the same here as on their own screens. Every amount is converted into the requested
 * currency: what happened in a period at the rate of its own date, what stands open today at
 * today's rate. Nexus never guesses a rate, so an amount without one is left out and reported.
 */
@Service
public class ReportService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PurchaseService purchases;
    private final ShipmentService shipments;
    private final SaleService sales;
    private final ExpenseService expenses;
    private final WriteOffService writeOffs;
    private final StockService stock;
    private final PaymentService payments;
    private final ExchangeRateService rates;

    ReportService(PurchaseService purchases, ShipmentService shipments, SaleService sales, ExpenseService expenses,
                  WriteOffService writeOffs, StockService stock, PaymentService payments, ExchangeRateService rates) {
        this.purchases = purchases;
        this.shipments = shipments;
        this.sales = sales;
        this.expenses = expenses;
        this.writeOffs = writeOffs;
        this.stock = stock;
        this.payments = payments;
        this.rates = rates;
    }

    /**
     * The figures for a period, both days included.
     *
     * @param from     first day, or null for no limit
     * @param to       last day, or null for no limit
     * @param currency the currency to show everything in
     */
    @Transactional(readOnly = true)
    public ReportSummary summarize(LocalDate from, LocalDate to, String currency) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("The period must not start after it ends");
        }
        String target = requireCurrency(currency);
        Converter converter = new Converter(target);
        Period period = new Period(from, to);

        List<Purchase> allPurchases = purchases.listPurchases();
        List<Sale> allSales = sales.listSales();
        List<Expense> allExpenses = expenses.listExpenses();
        boolean hasActivity = !allPurchases.isEmpty() || !allSales.isEmpty() || !allExpenses.isEmpty();

        List<Sale> salesInPeriod = period.keep(allSales, Sale::saleDate);
        List<Expense> expensesInPeriod = period.keep(allExpenses, Expense::expenseDate);
        List<WriteOff> writeOffsInPeriod = period.keep(writeOffs.listWriteOffs(), WriteOff::date);

        Money boughtFor = converter.total(period.keep(allPurchases, Purchase::purchaseDate),
                Purchase::total, Purchase::purchaseDate);
        Money shipping = converter.total(period.keep(shipments.listShipments(), Shipment::shippedDate),
                Shipment::cost, Shipment::shippedDate);
        Money revenue = converter.total(salesInPeriod, Sale::total, Sale::saleDate)
                .minus(converter.total(salesInPeriod, Sale::returns, Sale::saleDate));
        Money spent = converter.total(expensesInPeriod, Expense::amount, Expense::expenseDate);
        Money losses = Money.zero(target);
        for (WriteOff writeOff : writeOffsInPeriod) {
            losses = losses.plus(converter.total(writeOff.cost(), part -> part, part -> writeOff.date()));
        }

        Money salesProfit = Money.zero(target);
        int unknownProfit = 0;
        for (Sale sale : salesInPeriod) {
            Optional<Money> profit = sale.profit() == null ? Optional.empty()
                    : converter.convert(sale.profit(), sale.saleDate());
            if (profit.isPresent()) {
                salesProfit = salesProfit.plus(profit.get());
            } else {
                unknownProfit++;
            }
        }
        // Costs on a piece are already in that piece's cost, and costs on a sale already lower its
        // profit: only general costs (rent, fees…) still have to come off the profit.
        Money generalCosts = converter.total(
                expensesInPeriod.stream().filter(e -> e.unitId() == null && e.saleId() == null).toList(),
                Expense::amount, Expense::expenseDate);
        Money profit = salesProfit.minus(generalCosts).minus(losses);

        return new ReportSummary(target, hasActivity, boughtFor, shipping, boughtFor.plus(shipping), revenue, spent,
                losses, spent.plus(losses), profit, marginPercent(profit, revenue), salesInPeriod.size(), unknownProfit,
                converter.missing());
    }

    /** Stock value and open balances today, at today's rate. */
    @Transactional(readOnly = true)
    public ReportPosition position(String currency) {
        String target = requireCurrency(currency);
        Converter converter = new Converter(target);
        LocalDate today = LocalDate.now();

        Money stockValue = Money.zero(target);
        for (Map.Entry<String, BigDecimal> value : stock.stockValueByCurrency().entrySet()) {
            Money amount = Money.of(value.getValue().toPlainString(), value.getKey());
            stockValue = stockValue.plus(converter.convert(amount, today).orElse(Money.zero(target)));
        }
        return new ReportPosition(target, stockValue, openBalances(payments.receivables(), converter, today),
                openBalances(payments.payables(), converter, today), converter.missing());
    }

    private static OpenBalances openBalances(List<OpenBalance> balances, Converter converter, LocalDate today) {
        Money total = Money.zero(converter.target);
        List<Row> rows = new java.util.ArrayList<>();
        for (OpenBalance balance : balances) {
            Optional<Money> converted = converter.convert(balance.balance(), today);
            total = total.plus(converted.orElse(Money.zero(converter.target)));
            rows.add(new Row(balance.reference(), balance.counterparty(), balance.date(), balance.daysOpen(),
                    balance.balance(), converted.orElse(null)));
        }
        return new OpenBalances(total, rows);
    }

    private static BigDecimal marginPercent(Money profit, Money revenue) {
        if (revenue.amount().signum() <= 0) {
            return null;
        }
        return profit.amount().multiply(HUNDRED).divide(revenue.amount(), 1, RoundingMode.HALF_UP);
    }

    private static String requireCurrency(String code) {
        try {
            return Currency.getInstance(code.strip().toUpperCase()).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Unknown currency: " + code);
        }
    }

    /** Converts amounts into one currency for one report, and remembers which rates were missing. */
    private final class Converter {

        private final String target;
        private final Map<String, LocalDate> missing = new TreeMap<>();

        Converter(String target) {
            this.target = target;
        }

        Optional<Money> convert(Money amount, LocalDate day) {
            Optional<Money> converted = rates.convert(amount, target, day);
            if (converted.isEmpty()) {
                // A rate covers its own date and every later one, so the earliest day is the one to name.
                missing.merge(amount.currency().getCurrencyCode(), day, (a, b) -> a.isBefore(b) ? a : b);
            }
            return converted;
        }

        <T> Money total(List<T> items, Function<T, Money> amount, Function<T, LocalDate> day) {
            Money total = Money.zero(target);
            for (T item : items) {
                total = total.plus(convert(amount.apply(item), day.apply(item)).orElse(Money.zero(target)));
            }
            return total;
        }

        List<MissingRate> missing() {
            return missing.entrySet().stream()
                    .map(entry -> new MissingRate(entry.getKey(), target, entry.getValue()))
                    .toList();
        }
    }

    private record Period(LocalDate from, LocalDate to) {

        <T> List<T> keep(List<T> items, Function<T, LocalDate> date) {
            return items.stream().filter(item -> contains(date.apply(item))).toList();
        }

        private boolean contains(LocalDate day) {
            return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
        }
    }
}
