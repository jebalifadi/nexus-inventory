package com.nexus.core.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Category;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.expense.ExpenseCategory;
import com.nexus.core.expense.ExpenseService;
import com.nexus.core.fx.ExchangeRateService;
import com.nexus.core.money.Money;
import com.nexus.core.purchase.NewPurchaseLine;
import com.nexus.core.purchase.PurchaseService;
import com.nexus.core.purchase.Supplier;
import com.nexus.core.sale.NewSaleLine;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shipment.AllocationMethod;
import com.nexus.core.shipment.Shipment;
import com.nexus.core.shipment.ShipmentService;
import com.nexus.core.stock.InventoryLot;
import com.nexus.core.stock.InventoryUnit;
import com.nexus.core.stock.Location;
import com.nexus.core.stock.StockService;
import com.nexus.core.writeoff.WriteOffService;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

/**
 * The dashboard figures, worked out on the backend. Each test uses its own months far in the past,
 * because the test classes share one database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class ReportIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private PurchaseService purchases;

    @Autowired
    private ShipmentService shipments;

    @Autowired
    private SaleService sales;

    @Autowired
    private ExpenseService expenses;

    @Autowired
    private WriteOffService writeOffs;

    @Autowired
    private ExchangeRateService rates;

    @Autowired
    private ReportService reports;

    @Test
    void addsUpAPeriodAndLeavesOutWhatIsOutsideIt() {
        LocalDate day = LocalDate.of(2019, 3, 10);
        Category phones = catalog.createCategory("RP phones", TrackingMode.UNIT, List.of());
        Category cables = catalog.createCategory("RP cables", TrackingMode.LOT, List.of());
        Product phone = catalog.createProduct(phones.id(), "RP-PHONE", Map.of());
        Product cable = catalog.createProduct(cables.id(), "RP-CABLE", Map.of());
        Location berlin = stock.createLocation("RP Berlin", "Germany");
        Location tunis = stock.createLocation("RP Tunis", "Tunisia");
        Supplier supplier = purchases.createSupplier("RP supplier", "Germany", null, null);

        // Bought: 10 cables at 4 EUR = 40 EUR.
        purchases.createPurchase(supplier.id(), day, "EUR", null,
                List.of(new NewPurchaseLine(cable.id(), berlin.id(), 10, "4", null)));

        // A phone costing 300 + 20 shipping + 15 repair = 335 EUR, sold for 500: 165 EUR profit.
        InventoryUnit sold = stock.receiveUnit(phone.id(), berlin.id(), Money.of("300", "EUR"), day, Map.of());
        Shipment shipment = shipments.createShipment(berlin.id(), tunis.id(), null, null, Money.of("20", "EUR"),
                AllocationMethod.BY_QUANTITY, day, List.of(sold.publicId()), List.of());
        shipments.markArrived(shipment.publicId(), day.plusDays(3));
        expenses.addExpense(ExpenseCategory.REPAIR, Money.of("15", "EUR"), day.plusDays(4), "Screen",
                sold.publicId(), null);
        sales.createSale(null, tunis.id(), day.plusDays(5), "EUR", null,
                List.of(new NewSaleLine(sold.publicId(), null, null, "500", 0, null)));

        // General cost: comes off the profit. Lost batch: a 25 EUR loss.
        expenses.addExpense(ExpenseCategory.OTHER, Money.of("30", "EUR"), day, "Rent", null, null);
        InventoryLot lost = stock.receiveLot(cable.id(), berlin.id(), 10, Money.of("2.50", "EUR"), day);
        var writeOff = writeOffs.writeOffLot(lost.publicId(), "Lost", day);

        // Next month: must not count in March.
        InventoryUnit later = stock.receiveUnit(phone.id(), tunis.id(), Money.of("100", "EUR"), day, Map.of());
        sales.createSale(null, tunis.id(), LocalDate.of(2019, 4, 2), "EUR", null,
                List.of(new NewSaleLine(later.publicId(), null, null, "999", 0, null)));

        ReportSummary march = figures(LocalDate.of(2019, 3, 1), LocalDate.of(2019, 3, 31), "EUR");

        assertThat(march.boughtFor()).isEqualTo(Money.of("40", "EUR"));
        assertThat(march.shipping()).isEqualTo(Money.of("20", "EUR"));
        assertThat(march.invested()).isEqualTo(Money.of("60", "EUR"));
        assertThat(march.revenue()).isEqualTo(Money.of("500", "EUR"));
        assertThat(march.expenses()).isEqualTo(Money.of("45", "EUR"));
        assertThat(march.losses()).isEqualTo(Money.of("25", "EUR"));
        assertThat(march.costs()).isEqualTo(Money.of("70", "EUR"));
        assertThat(march.profit()).isEqualTo(Money.of("110", "EUR")); // 165 - 30 - 25
        assertThat(march.marginPercent()).isEqualByComparingTo("22.0");
        assertThat(march.salesCount()).isEqualTo(1);
        assertThat(march.unknownProfitCount()).isZero();

        // Both ends of the period count: the sale on 2 April is in a period of that one day.
        assertThat(figures(LocalDate.of(2019, 4, 2), LocalDate.of(2019, 4, 2), "EUR").revenue())
                .isEqualTo(Money.of("999", "EUR"));

        // Found again: the loss stops counting.
        stock.restoreLot(lost.publicId());
        assertThat(figures(LocalDate.of(2019, 3, 1), LocalDate.of(2019, 3, 31), "EUR").losses())
                .as("write-off %s was cancelled", writeOff.publicId())
                .isEqualTo(Money.zero("EUR"));
    }

    @Test
    void returnsLowerRevenueAndEachCurrencyStaysApart() {
        LocalDate day = LocalDate.of(2018, 5, 10);
        Category phones = catalog.createCategory("RP2 phones", TrackingMode.UNIT, List.of());
        Product phone = catalog.createProduct(phones.id(), "RP2-PHONE", Map.of());
        Location shop = stock.createLocation("RP2 shop", "Tunisia");

        InventoryUnit returned = stock.receiveUnit(phone.id(), shop.id(), Money.of("100", "EUR"), day, Map.of());
        Sale sale = sales.createSale(null, shop.id(), day, "EUR", null,
                List.of(new NewSaleLine(returned.publicId(), null, null, "200", 0, null)));
        sales.returnGoods(sale.publicId(), 1, 1);
        Money profitAfterReturn = sales.getSale(sale.publicId()).profit();

        // Cost in EUR, sold in TND, and no exchange rate that far back: the profit is unknown, not guessed.
        InventoryUnit noRate = stock.receiveUnit(phone.id(), shop.id(), Money.of("300", "EUR"), day, Map.of());
        sales.createSale(null, shop.id(), day, "TND", null,
                List.of(new NewSaleLine(noRate.publicId(), null, null, "1400", 0, null)));

        LocalDate from = LocalDate.of(2018, 5, 1);
        LocalDate to = LocalDate.of(2018, 5, 31);
        ReportSummary euros = figures(from, to, "EUR");
        ReportSummary dinars = figures(from, to, "TND");

        // The returned sale nets to nothing; the dinar sale cannot be converted, and the report says so.
        assertThat(euros.revenue()).isEqualTo(Money.zero("EUR"));
        assertThat(euros.profit()).isEqualTo(profitAfterReturn);
        assertThat(euros.marginPercent()).isNull();
        assertThat(euros.salesCount()).isEqualTo(2);
        assertThat(euros.unknownProfitCount()).isEqualTo(1);
        assertThat(euros.missingRates()).containsExactly(new MissingRate("TND", "EUR", day));

        // Nothing is guessed: only the dinar sale counts in dinars, and the euro amounts are reported.
        assertThat(dinars.revenue()).isEqualTo(Money.of("1400", "TND"));
        assertThat(dinars.profit()).isEqualTo(Money.zero("TND"));
        assertThat(dinars.unknownProfitCount()).isEqualTo(2);
        assertThat(dinars.marginPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dinars.missingRates()).containsExactly(new MissingRate("EUR", "TND", day));
    }

    @Test
    void everyAmountIsConvertedAtTheRateOfItsOwnDate() {
        // EUR and USD: the other test classes rely on having no EUR to TND rate this far back.
        LocalDate day = LocalDate.of(2017, 3, 10);
        rates.addRate("EUR", "USD", new BigDecimal("1.5"), LocalDate.of(2017, 1, 1));
        rates.addRate("EUR", "USD", new BigDecimal("2"), LocalDate.of(2017, 6, 1));
        Category phones = catalog.createCategory("RP3 phones", TrackingMode.UNIT, List.of());
        Product phone = catalog.createProduct(phones.id(), "RP3-PHONE", Map.of());
        Location shop = stock.createLocation("RP3 shop", "Germany");
        InventoryUnit unit = stock.receiveUnit(phone.id(), shop.id(), Money.of("60", "EUR"), day, Map.of());
        Sale sale = sales.createSale(null, shop.id(), day, "EUR", null,
                List.of(new NewSaleLine(unit.publicId(), null, null, "100", 0, null)));
        expenses.addExpense(ExpenseCategory.OTHER, Money.of("30", "USD"), day, "Rent", null, null);
        LocalDate from = LocalDate.of(2017, 3, 1);
        LocalDate to = LocalDate.of(2017, 3, 31);

        // In dollars, at the March rate (1.5) and not the later one (2): 150 revenue, 60 - 30 profit.
        ReportSummary dollars = figures(from, to, "USD");
        assertThat(dollars.currency()).isEqualTo("USD");
        assertThat(dollars.revenue()).isEqualTo(Money.of("150", "USD"));
        assertThat(dollars.expenses()).isEqualTo(Money.of("30", "USD"));
        assertThat(dollars.profit()).isEqualTo(Money.of("30", "USD"));
        assertThat(dollars.marginPercent()).isEqualByComparingTo("20.0");
        assertThat(dollars.missingRates()).isEmpty();

        // In euros, the same rate works backwards: 30 USD / 1.5 = 20 EUR.
        ReportSummary euros = figures(from, to, "EUR");
        assertThat(euros.revenue()).isEqualTo(Money.of("100", "EUR"));
        assertThat(euros.expenses()).isEqualTo(Money.of("20", "EUR"));
        assertThat(euros.profit()).isEqualTo(Money.of("20", "EUR"));

        // What stands open today uses today's rate (2): the unpaid 100 EUR is 200 USD.
        ReportPosition.Row owed = reports.position("USD").owedToYou().rows().stream()
                .filter(row -> row.reference().equals(sale.publicId()))
                .findFirst()
                .orElseThrow();
        assertThat(owed.balance()).isEqualTo(Money.of("100", "EUR"));
        assertThat(owed.converted()).isEqualTo(Money.of("200", "USD"));

        // 10 pieces at 3 EUR add 30 EUR of stock: 60 USD today.
        BigDecimal before = reports.position("USD").stockValue().amount();
        stock.receiveLot(catalog.createProduct(
                catalog.createCategory("RP3 cables", TrackingMode.LOT, List.of()).id(), "RP3-CABLE", Map.of()).id(),
                shop.id(), 10, Money.of("3", "EUR"), day);
        assertThat(reports.position("USD").stockValue().amount().subtract(before)).isEqualByComparingTo("60");
    }

    @Test
    void refusesAPeriodThatEndsBeforeItStarts() {
        assertThatThrownBy(() -> reports.summarize(LocalDate.of(2019, 2, 1), LocalDate.of(2019, 1, 1), "EUR"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("start");
    }

    @Test
    void theApiNeedsLoginAndReadsDatesAndMoneyAsText() throws Exception {
        assertThat(get("/api/v1/reports/summary?currency=EUR", null).statusCode()).isIn(401, 403);

        String owner = login();
        HttpResponse<String> emptyPeriod = get("/api/v1/reports/summary?currency=TND&from=2001-01-01&to=2001-01-31", owner);
        assertThat(emptyPeriod.statusCode()).isEqualTo(200);
        assertThat(emptyPeriod.body()).contains("\"currency\":\"TND\"").contains("\"revenue\":{\"amount\":\"0.000\"");

        assertThat(get("/api/v1/reports/summary?currency=EUR", owner).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/reports/position?currency=EUR", owner).statusCode()).isEqualTo(200);
        assertThat(get("/api/v1/reports/summary", owner).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/reports/summary?currency=NOPE", owner).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/reports/summary?currency=EUR&from=not-a-date", owner).statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/reports/summary?currency=EUR&from=2019-02-01&to=2019-01-01", owner).statusCode()).isEqualTo(400);
    }

    private ReportSummary figures(LocalDate from, LocalDate to, String currency) {
        return reports.summarize(from, to, currency);
    }

    private String login() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"owner\",\"password\":\"change-me-please\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher matcher = TOKEN.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getProperty("local.server.port") + path);
    }
}
