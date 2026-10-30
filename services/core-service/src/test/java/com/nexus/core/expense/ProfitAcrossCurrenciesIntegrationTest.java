package com.nexus.core.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Category;
import com.nexus.core.catalog.FieldDefinition;
import com.nexus.core.catalog.FieldScope;
import com.nexus.core.catalog.FieldType;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.fx.ExchangeRateService;
import com.nexus.core.money.Money;
import com.nexus.core.sale.NewSaleLine;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.stock.InventoryUnit;
import com.nexus.core.stock.Location;
import com.nexus.core.stock.StockService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** A phone bought in euros and sold in dinars: profit with the historical rate, repairs and fees. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class ProfitAcrossCurrenciesIntegrationTest {

    private static final LocalDate SALE_DAY = LocalDate.of(2026, 9, 18);

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private SaleService sales;

    @Autowired
    private ExchangeRateService rates;

    @Autowired
    private ExpenseService expenses;

    @Test
    void profitUsesTheRateOfTheSaleDayAndIncludesExpenses() {
        Category phones = catalog.createCategory("Fx phones", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product iphone = catalog.createProduct(phones.id(), "FX-IP14", Map.of("modell", "iPhone 14 Pro"));
        Location monastir = stock.createLocation("Monastir shop", "Tunisia");
        InventoryUnit unit = stock.receiveUnit(iphone.id(), monastir.id(), Money.of("323", "EUR"),
                LocalDate.of(2026, 9, 1), Map.of());
        Sale sale = sales.createSale(null, monastir.id(), SALE_DAY, "TND", null,
                List.of(new NewSaleLine(unit.publicId(), null, null, "1400", 0, null)));

        // No EUR to TND rate yet: Nexus does not guess.
        assertThat(sale.profit()).isNull();
        assertThat(sale.profitUnknownReason()).contains("EUR").contains("TND");

        rates.addRate("EUR", "TND", new BigDecimal("3.3798"), LocalDate.of(2026, 9, 15));
        rates.addRate("EUR", "TND", new BigDecimal("3.5000"), LocalDate.of(2026, 9, 20));

        // 323 EUR x 3.3798 (the rate valid on the sale day, not the later one) = 1091.675 TND
        Sale withRate = sales.getSale(sale.publicId());
        assertThat(withRate.lines().get(0).cost()).isEqualTo(Money.of("1091.675", "TND"));
        assertThat(withRate.profit()).isEqualTo(Money.of("308.325", "TND"));

        expenses.addExpense(ExpenseCategory.REPAIR, Money.of("20", "EUR"), null, "New battery", unit.publicId(), null);
        expenses.addExpense(ExpenseCategory.PLATFORM_FEES, Money.of("30", "TND"), null, null, null, sale.publicId());

        // Repair: 20 EUR x 3.3798 = 67.596 TND. Fees: 30 TND.
        Sale withExpenses = sales.getSale(sale.publicId());
        assertThat(withExpenses.lines().get(0).cost()).isEqualTo(Money.of("1159.271", "TND"));
        assertThat(withExpenses.saleExpenses()).isEqualTo(Money.of("30", "TND"));
        assertThat(withExpenses.profit()).isEqualTo(Money.of("210.729", "TND"));
        assertThat(withExpenses.profitUnknownReason()).isNull();
    }

    @Test
    void convertsBothWaysAndRefusesUnclearExpenses() {
        // USD here, so this test never gives the other one an EUR to TND rate by accident.
        rates.addRate("USD", "TND", new BigDecimal("3.1250"), LocalDate.of(2026, 1, 1));

        assertThat(rates.convert(Money.of("3.125", "TND"), "USD", LocalDate.of(2026, 1, 2)))
                .contains(Money.of("1.00", "USD"));
        assertThat(rates.convert(Money.of("10", "USD"), "TND", LocalDate.of(2025, 12, 31))).isEmpty();
        assertThatThrownBy(() -> rates.addRate("EUR", "EUR", BigDecimal.ONE, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> expenses.addExpense(ExpenseCategory.OTHER, Money.of("5", "EUR"), null, null,
                "U-000001", "S-000001"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not both");
    }
}
