package com.nexus.core.report;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Category;
import com.nexus.core.catalog.Product;
import com.nexus.core.fx.ExchangeRateService;
import com.nexus.core.money.Money;
import com.nexus.core.report.SalesAnalytics.CategorySales;
import com.nexus.core.report.SalesAnalytics.ProductSales;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleLine;
import com.nexus.core.sale.SaleService;
import com.nexus.core.settings.BusinessSettingsService;
import com.nexus.core.stock.StockService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What sells, what earns and what sits on the shelf. */
@Service
public class AnalyticsService {

    private final SaleService sales;
    private final CatalogService catalog;
    private final StockService stock;
    private final ExchangeRateService rates;
    private final BusinessSettingsService settings;

    AnalyticsService(SaleService sales, CatalogService catalog, StockService stock, ExchangeRateService rates,
                     BusinessSettingsService settings) {
        this.sales = sales;
        this.catalog = catalog;
        this.stock = stock;
        this.rates = rates;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public SalesAnalytics salesAnalytics(LocalDate from, LocalDate to, String currency) {
        ReportPeriod period = new ReportPeriod(from, to);
        ReportConverter converter = new ReportConverter(rates, currency);
        Map<Long, Product> products = products();
        Map<Long, String> categoryNames = catalog.listCategories().stream()
                .collect(Collectors.toMap(Category::id, Category::name));

        Map<Long, Totals> byProduct = new LinkedHashMap<>();
        Map<String, Totals> byCategory = new LinkedHashMap<>();
        for (Sale sale : period.keep(sales.listSales(), Sale::saleDate)) {
            for (SaleLine line : sale.lines()) {
                int sold = line.quantity() - line.returnedQuantity();
                Money net = line.lineTotal().minus(line.unitPrice().times(line.returnedQuantity()));
                Money revenue = converter.convertOrZero(net, sale.saleDate());
                Optional<Money> profit = line.profit() == null ? Optional.empty()
                        : converter.convert(line.profit(), sale.saleDate());
                Product product = products.get(line.productId());
                String category = product == null ? null : categoryNames.get(product.categoryId());
                byProduct.computeIfAbsent(line.productId(), id -> new Totals(converter.zero()))
                        .add(sold, revenue, profit);
                byCategory.computeIfAbsent(category, name -> new Totals(converter.zero()))
                        .add(sold, revenue, profit);
            }
        }

        List<ProductSales> productRows = byProduct.entrySet().stream()
                .map(entry -> {
                    Product product = products.get(entry.getKey());
                    Totals t = entry.getValue();
                    return new ProductSales(entry.getKey(), product == null ? null : product.sku(),
                            product == null ? null : product.name(),
                            product == null ? null : categoryNames.get(product.categoryId()), t.quantity, t.revenue,
                            t.profit, t.unknownProfit);
                })
                .sorted(Comparator.comparingInt(ProductSales::quantity).reversed())
                .toList();
        List<CategorySales> categoryRows = byCategory.entrySet().stream()
                .map(entry -> {
                    Totals t = entry.getValue();
                    return new CategorySales(entry.getKey(), t.quantity, t.revenue, t.profit, t.unknownProfit);
                })
                .sorted(Comparator.comparing((CategorySales c) -> c.profit().amount()).reversed())
                .toList();
        return new SalesAnalytics(converter.target(), productRows, categoryRows, converter.missing());
    }

    @Transactional(readOnly = true)
    public SlowStock slowStock(String currency) {
        ReportConverter converter = new ReportConverter(rates, currency);
        LocalDate today = LocalDate.now();
        int limit = settings.current().slowStockDays();
        LocalDate cutoff = today.minusDays(limit);

        Map<Long, Waiting> byProduct = new LinkedHashMap<>();
        stock.listUnits().stream()
                .filter(u -> u.status().isInStock() && u.receivedDate().isBefore(cutoff))
                .forEach(u -> byProduct.computeIfAbsent(u.productId(), id -> new Waiting(converter.zero()))
                        .add(1, u.receivedDate(), converter.convertOrZero(u.unitCost(), today)));
        stock.listLots().stream()
                .filter(l -> "IN_STOCK".equals(l.status()) && l.quantityRemaining() > 0
                        && l.receivedDate().isBefore(cutoff))
                .forEach(l -> byProduct.computeIfAbsent(l.productId(), id -> new Waiting(converter.zero()))
                        .add(l.quantityRemaining(), l.receivedDate(),
                                converter.convertOrZero(l.unitCost().times(l.quantityRemaining()), today)));

        Map<Long, Product> products = products();
        List<SlowStock.Row> rows = new ArrayList<>();
        byProduct.forEach((id, w) -> {
            Product product = products.get(id);
            rows.add(new SlowStock.Row(id, product == null ? null : product.sku(),
                    product == null ? null : product.name(), w.quantity, w.oldest,
                    ChronoUnit.DAYS.between(w.oldest, today), w.value));
        });
        rows.sort(Comparator.comparing(SlowStock.Row::oldest));
        return new SlowStock(converter.target(), limit, rows, converter.missing());
    }

    private Map<Long, Product> products() {
        return catalog.listProducts(null).stream().collect(Collectors.toMap(Product::id, Function.identity()));
    }

    private static final class Totals {
        private int quantity;
        private Money revenue;
        private Money profit;
        private boolean unknownProfit;

        Totals(Money zero) {
            revenue = zero;
            profit = zero;
        }

        void add(int sold, Money lineRevenue, Optional<Money> lineProfit) {
            quantity += sold;
            revenue = revenue.plus(lineRevenue);
            if (lineProfit.isPresent()) {
                profit = profit.plus(lineProfit.get());
            } else {
                unknownProfit = true;
            }
        }
    }

    private static final class Waiting {
        private int quantity;
        private LocalDate oldest;
        private Money value;

        Waiting(Money zero) {
            value = zero;
        }

        void add(int pieces, LocalDate received, Money cost) {
            quantity += pieces;
            value = value.plus(cost);
            if (oldest == null || received.isBefore(oldest)) {
                oldest = received;
            }
        }
    }
}
