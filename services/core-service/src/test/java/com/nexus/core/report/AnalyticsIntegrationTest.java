package com.nexus.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Category;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.money.Money;
import com.nexus.core.sale.NewSaleLine;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleService;
import com.nexus.core.stock.Location;
import com.nexus.core.stock.StockService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Best sellers, profit per product and category, the monthly trend and slow stock. Dates far back: one shared database. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class AnalyticsIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private SaleService sales;

    @Autowired
    private AnalyticsService analytics;

    @Autowired
    private ReportService reports;

    @Test
    void addsUpSalesPerProductAndCategoryAndAcrossMonths() {
        Category cables = catalog.createCategory("AN cables", TrackingMode.LOT, List.of());
        Product usb = catalog.createProduct(cables.id(), "AN-USB", Map.of());
        Product hdmi = catalog.createProduct(cables.id(), "AN-HDMI", Map.of());
        Location shop = stock.createLocation("AN shop", "Germany");
        LocalDate january = LocalDate.of(2015, 1, 20);
        LocalDate march = LocalDate.of(2015, 3, 5);
        stock.receiveLot(usb.id(), shop.id(), 10, Money.of("4", "EUR"), january);
        stock.receiveLot(hdmi.id(), shop.id(), 10, Money.of("2", "EUR"), january);

        // 5 USB at 10, one returned: 4 sold, 40 revenue, 4 x (10 - 4) = 24 profit.
        Sale first = sales.createSale(null, shop.id(), january, "EUR", null,
                List.of(new NewSaleLine(null, usb.id(), 5, "10", 0, null)));
        sales.returnGoods(first.publicId(), 1, 1);
        // 1 HDMI at 5 in March: 3 profit.
        sales.createSale(null, shop.id(), march, "EUR", null,
                List.of(new NewSaleLine(null, hdmi.id(), 1, "5", 0, null)));

        LocalDate from = LocalDate.of(2015, 1, 1);
        LocalDate to = LocalDate.of(2015, 3, 31);
        SalesAnalytics result = analytics.salesAnalytics(from, to, "EUR");

        assertThat(result.products()).extracting(SalesAnalytics.ProductSales::sku).containsExactly("AN-USB", "AN-HDMI");
        SalesAnalytics.ProductSales best = result.products().get(0);
        assertThat(best.quantity()).isEqualTo(4);
        assertThat(best.revenue()).isEqualTo(Money.of("40", "EUR"));
        assertThat(best.profit()).isEqualTo(Money.of("24", "EUR"));
        assertThat(best.categoryName()).isEqualTo("AN cables");
        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).quantity()).isEqualTo(5);
        assertThat(result.categories().get(0).profit()).isEqualTo(Money.of("27", "EUR"));

        // A month without sales still appears, and each month matches the dashboard for that month.
        MonthlyTrend trend = reports.trend(from, to, "EUR");
        assertThat(trend.months()).extracting(MonthlyTrend.Month::month).containsExactly("2015-01", "2015-02", "2015-03");
        assertThat(trend.months().get(0).revenue()).isEqualTo(Money.of("40", "EUR"));
        assertThat(trend.months().get(1).revenue()).isEqualTo(Money.zero("EUR"));
        assertThat(trend.months().get(2).profit()).isEqualTo(
                reports.summarize(LocalDate.of(2015, 3, 1), LocalDate.of(2015, 3, 31), "EUR").profit());

        // A period starting mid-month counts only the days inside it.
        assertThat(reports.trend(LocalDate.of(2015, 1, 21), to, "EUR").months().get(0).revenue())
                .isEqualTo(Money.zero("EUR"));
    }

    @Test
    void slowStockListsOnlyGoodsOlderThanTheLimit() {
        Category bags = catalog.createCategory("AN bags", TrackingMode.LOT, List.of());
        Category phones = catalog.createCategory("AN phones", TrackingMode.UNIT, List.of());
        Product old = catalog.createProduct(bags.id(), "AN-OLD", Map.of());
        Product fresh = catalog.createProduct(bags.id(), "AN-FRESH", Map.of());
        Product soldPhone = catalog.createProduct(phones.id(), "AN-SOLD", Map.of());
        Location shop = stock.createLocation("AN shelf", "Germany");
        LocalDate longAgo = LocalDate.now().minusDays(200);
        stock.receiveLot(old.id(), shop.id(), 3, Money.of("2", "EUR"), longAgo);
        stock.receiveLot(fresh.id(), shop.id(), 3, Money.of("2", "EUR"), LocalDate.now().minusDays(5));
        var phone = stock.receiveUnit(soldPhone.id(), shop.id(), Money.of("9", "EUR"), longAgo, Map.of());
        sales.createSale(null, shop.id(), LocalDate.now(), "EUR", null,
                List.of(new NewSaleLine(phone.publicId(), null, null, "20", 0, null)));

        SlowStock result = analytics.slowStock("EUR");

        assertThat(result.days()).isEqualTo(90);
        assertThat(result.products()).extracting(SlowStock.Row::sku).contains("AN-OLD").doesNotContain("AN-FRESH", "AN-SOLD");
        SlowStock.Row row = result.products().stream().filter(r -> "AN-OLD".equals(r.sku())).findFirst().orElseThrow();
        assertThat(row.quantity()).isEqualTo(3);
        assertThat(row.daysInStock()).isEqualTo(200);
        assertThat(row.value()).isEqualTo(Money.of("6", "EUR"));
    }
}
