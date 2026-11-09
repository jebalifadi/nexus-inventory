package com.nexus.core.report;

import com.nexus.core.report.ReportPosition.OpenBalances;
import com.nexus.core.report.ReportPosition.Row;
import com.nexus.core.web.MoneyJson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for the business figures shown on the dashboard. */
@RestController
@RequestMapping("/api/v1/reports")
class ReportController {

    record SummaryView(String currency, boolean hasActivity, MoneyJson boughtFor, MoneyJson shipping,
                       MoneyJson invested, MoneyJson revenue, MoneyJson expenses, MoneyJson losses, MoneyJson costs,
                       MoneyJson profit, BigDecimal marginPercent, int salesCount, int unknownProfitCount,
                       List<MissingRate> missingRates) {

        static SummaryView of(ReportSummary s) {
            return new SummaryView(s.currency(), s.hasActivity(), MoneyJson.of(s.boughtFor()),
                    MoneyJson.of(s.shipping()), MoneyJson.of(s.invested()), MoneyJson.of(s.revenue()),
                    MoneyJson.of(s.expenses()), MoneyJson.of(s.losses()), MoneyJson.of(s.costs()),
                    MoneyJson.of(s.profit()), s.marginPercent(), s.salesCount(), s.unknownProfitCount(),
                    s.missingRates());
        }
    }

    record RowView(String reference, String counterparty, LocalDate date, long daysOpen, MoneyJson balance,
                   MoneyJson converted, boolean overdue) {

        static RowView of(Row r) {
            return new RowView(r.reference(), r.counterparty(), r.date(), r.daysOpen(), MoneyJson.of(r.balance()),
                    MoneyJson.of(r.converted()), r.overdue());
        }
    }

    record OpenBalancesView(MoneyJson total, List<RowView> rows) {

        static OpenBalancesView of(OpenBalances b) {
            return new OpenBalancesView(MoneyJson.of(b.total()), b.rows().stream().map(RowView::of).toList());
        }
    }

    record PositionView(String currency, MoneyJson stockValue, OpenBalancesView owedToYou, OpenBalancesView youOwe,
                        List<MissingRate> missingRates) {
    }

    record MonthView(String month, MoneyJson revenue, MoneyJson profit, int salesCount, int unknownProfitCount) {
    }

    record TrendView(String currency, List<MonthView> months, List<MissingRate> missingRates) {
    }

    record ProductView(long productId, String sku, String name, String categoryName, int quantity, MoneyJson revenue,
                       MoneyJson profit, boolean unknownProfit) {
    }

    record CategoryView(String categoryName, int quantity, MoneyJson revenue, MoneyJson profit, boolean unknownProfit) {
    }

    record SalesAnalyticsView(String currency, List<ProductView> products, List<CategoryView> categories,
                              List<MissingRate> missingRates) {
    }

    record SlowRowView(long productId, String sku, String name, int quantity, LocalDate oldest, long daysInStock,
                       MoneyJson value) {
    }

    record SlowStockView(String currency, int days, List<SlowRowView> products, List<MissingRate> missingRates) {
    }

    private final ReportService reports;
    private final AnalyticsService analytics;

    ReportController(ReportService reports, AnalyticsService analytics) {
        this.reports = reports;
        this.analytics = analytics;
    }

    /** Everything in {@code currency}. Leave out {@code from} or {@code to} for no limit on that side. */
    @GetMapping("/summary")
    SummaryView summary(
            @RequestParam(name = "currency") String currency,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return SummaryView.of(reports.summarize(from, to, currency));
    }

    /** Revenue and profit per month, in {@code currency}. */
    @GetMapping("/trend")
    TrendView trend(@RequestParam(name = "currency") String currency,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        MonthlyTrend t = reports.trend(from, to, currency);
        return new TrendView(t.currency(), t.months().stream()
                .map(m -> new MonthView(m.month(), MoneyJson.of(m.revenue()), MoneyJson.of(m.profit()), m.salesCount(),
                        m.unknownProfitCount()))
                .toList(), t.missingRates());
    }

    /** What sold in the period, per product and per category. */
    @GetMapping("/sales-analytics")
    SalesAnalyticsView salesAnalytics(@RequestParam(name = "currency") String currency,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SalesAnalytics a = analytics.salesAnalytics(from, to, currency);
        return new SalesAnalyticsView(a.currency(), a.products().stream()
                .map(p -> new ProductView(p.productId(), p.sku(), p.name(), p.categoryName(), p.quantity(),
                        MoneyJson.of(p.revenue()), MoneyJson.of(p.profit()), p.unknownProfit()))
                .toList(), a.categories().stream()
                .map(c -> new CategoryView(c.categoryName(), c.quantity(), MoneyJson.of(c.revenue()),
                        MoneyJson.of(c.profit()), c.unknownProfit()))
                .toList(), a.missingRates());
    }

    /** Goods in stock for longer than the owner's limit. */
    @GetMapping("/slow-stock")
    SlowStockView slowStock(@RequestParam(name = "currency") String currency) {
        SlowStock s = analytics.slowStock(currency);
        return new SlowStockView(s.currency(), s.days(), s.products().stream()
                .map(r -> new SlowRowView(r.productId(), r.sku(), r.name(), r.quantity(), r.oldest(), r.daysInStock(),
                        MoneyJson.of(r.value())))
                .toList(), s.missingRates());
    }

    /** Stock value and open balances today, in {@code currency}. */
    @GetMapping("/position")
    PositionView position(@RequestParam(name = "currency") String currency) {
        ReportPosition p = reports.position(currency);
        return new PositionView(p.currency(), MoneyJson.of(p.stockValue()), OpenBalancesView.of(p.owedToYou()),
                OpenBalancesView.of(p.youOwe()), p.missingRates());
    }
}
