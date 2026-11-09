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
import com.nexus.core.settings.BusinessSettingsService;
import com.nexus.core.shipment.Shipment;
import com.nexus.core.shipment.ShipmentService;
import com.nexus.core.stock.StockService;
import com.nexus.core.writeoff.WriteOff;
import com.nexus.core.writeoff.WriteOffService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
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
    private final BusinessSettingsService settings;

    ReportService(PurchaseService purchases, ShipmentService shipments, SaleService sales, ExpenseService expenses,
                  WriteOffService writeOffs, StockService stock, PaymentService payments, ExchangeRateService rates,
                  BusinessSettingsService settings) {
        this.purchases = purchases;
        this.shipments = shipments;
        this.sales = sales;
        this.expenses = expenses;
        this.writeOffs = writeOffs;
        this.stock = stock;
        this.payments = payments;
        this.rates = rates;
        this.settings = settings;
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
        ReportPeriod period = new ReportPeriod(from, to);
        ReportConverter converter = new ReportConverter(rates, currency);
        return figures(loadActivity(), period, converter);
    }

    /** Month by month, using the same figures as {@link #summarize}. Without a start, from the first activity. */
    @Transactional(readOnly = true)
    public MonthlyTrend trend(LocalDate from, LocalDate to, String currency) {
        new ReportPeriod(from, to);
        ReportConverter converter = new ReportConverter(rates, currency);
        Activity activity = loadActivity();
        LocalDate start = from != null ? from : activity.firstDay().orElse(null);
        LocalDate end = to != null ? to : LocalDate.now();
        List<MonthlyTrend.Month> months = new ArrayList<>();
        if (start != null && !start.isAfter(end)) {
            for (YearMonth month = YearMonth.from(start); !month.isAfter(YearMonth.from(end)); month = month.plusMonths(1)) {
                LocalDate first = month.atDay(1).isBefore(start) ? start : month.atDay(1);
                LocalDate last = month.atEndOfMonth().isAfter(end) ? end : month.atEndOfMonth();
                ReportSummary f = figures(activity, new ReportPeriod(first, last), converter);
                months.add(new MonthlyTrend.Month(month.toString(), f.revenue(), f.profit(), f.salesCount(),
                        f.unknownProfitCount()));
            }
        }
        return new MonthlyTrend(converter.target(), months, converter.missing());
    }

    private Activity loadActivity() {
        return new Activity(purchases.listPurchases(), shipments.listShipments(), sales.listSales(),
                expenses.listExpenses(), writeOffs.listWriteOffs());
    }

    private static ReportSummary figures(Activity all, ReportPeriod period, ReportConverter converter) {
        boolean hasActivity = !all.purchases().isEmpty() || !all.sales().isEmpty() || !all.expenses().isEmpty();
        List<Sale> salesInPeriod = period.keep(all.sales(), Sale::saleDate);
        List<Expense> expensesInPeriod = period.keep(all.expenses(), Expense::expenseDate);

        Money boughtFor = converter.total(period.keep(all.purchases(), Purchase::purchaseDate),
                Purchase::total, Purchase::purchaseDate);
        Money shipping = converter.total(period.keep(all.shipments(), Shipment::shippedDate),
                Shipment::cost, Shipment::shippedDate);
        Money revenue = converter.total(salesInPeriod, Sale::total, Sale::saleDate)
                .minus(converter.total(salesInPeriod, Sale::returns, Sale::saleDate));
        Money spent = converter.total(expensesInPeriod, Expense::amount, Expense::expenseDate);
        Money losses = converter.zero();
        for (WriteOff writeOff : period.keep(all.writeOffs(), WriteOff::date)) {
            losses = losses.plus(converter.total(writeOff.cost(), part -> part, part -> writeOff.date()));
        }

        Money salesProfit = converter.zero();
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

        return new ReportSummary(converter.target(), hasActivity, boughtFor, shipping, boughtFor.plus(shipping), revenue,
                spent, losses, spent.plus(losses), profit, marginPercent(profit, revenue), salesInPeriod.size(),
                unknownProfit, converter.missing());
    }

    /** Stock value and open balances today, at today's rate. */
    @Transactional(readOnly = true)
    public ReportPosition position(String currency) {
        ReportConverter converter = new ReportConverter(rates, currency);
        LocalDate today = LocalDate.now();

        Money stockValue = converter.zero();
        for (Map.Entry<String, BigDecimal> value : stock.stockValueByCurrency().entrySet()) {
            Money amount = Money.of(value.getValue().toPlainString(), value.getKey());
            stockValue = stockValue.plus(converter.convertOrZero(amount, today));
        }
        int reminderDays = settings.current().paymentReminderDays();
        return new ReportPosition(converter.target(), stockValue, openBalances(payments.receivables(), converter, today, reminderDays),
                openBalances(payments.payables(), converter, today, reminderDays), converter.missing());
    }

    private static OpenBalances openBalances(List<OpenBalance> balances, ReportConverter converter, LocalDate today,
                                             int reminderDays) {
        Money total = converter.zero();
        List<Row> rows = new ArrayList<>();
        for (OpenBalance balance : balances) {
            Optional<Money> converted = converter.convert(balance.balance(), today);
            total = total.plus(converted.orElse(converter.zero()));
            rows.add(new Row(balance.reference(), balance.counterparty(), balance.date(), balance.daysOpen(),
                    balance.balance(), converted.orElse(null), balance.daysOpen() > reminderDays));
        }
        return new OpenBalances(total, rows);
    }

    private static BigDecimal marginPercent(Money profit, Money revenue) {
        if (revenue.amount().signum() <= 0) {
            return null;
        }
        return profit.amount().multiply(HUNDRED).divide(revenue.amount(), 1, RoundingMode.HALF_UP);
    }

    private record Activity(List<Purchase> purchases, List<Shipment> shipments, List<Sale> sales,
                            List<Expense> expenses, List<WriteOff> writeOffs) {

        Optional<LocalDate> firstDay() {
            return Stream.of(sales.stream().map(Sale::saleDate), expenses.stream().map(Expense::expenseDate),
                            writeOffs.stream().map(WriteOff::date))
                    .flatMap(days -> days)
                    .min(Comparator.naturalOrder());
        }
    }
}
