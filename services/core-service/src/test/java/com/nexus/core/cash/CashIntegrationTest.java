package com.nexus.core.cash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Category;
import com.nexus.core.catalog.FieldDefinition;
import com.nexus.core.catalog.FieldScope;
import com.nexus.core.catalog.FieldType;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.expense.ExpenseCategory;
import com.nexus.core.expense.ExpenseService;
import com.nexus.core.money.Money;
import com.nexus.core.payment.PaymentMethod;
import com.nexus.core.payment.PaymentService;
import com.nexus.core.purchase.NewPurchaseLine;
import com.nexus.core.purchase.Purchase;
import com.nexus.core.purchase.PurchaseService;
import com.nexus.core.purchase.Supplier;
import com.nexus.core.sale.NewSaleLine;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.stock.Location;
import com.nexus.core.stock.StockService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Follows money through the business in US dollars and checks cash, capital and business value.
 * Other tests share the database, so this one compares before and after.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class CashIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private PurchaseService purchases;

    @Autowired
    private SaleService sales;

    @Autowired
    private PaymentService payments;

    @Autowired
    private ExpenseService expenses;

    @Autowired
    private CashService cash;

    @Test
    void cashFollowsEveryMovementAndBusinessValueAddsUp() {
        CurrencyPosition usdBefore = position("USD");
        CurrencyPosition tndBefore = position("TND");
        Money capitalBefore = profitWithdrawn("USD");

        cash.recordCapital(CapitalType.CAPITAL_ADDED, Money.of("1000", "USD"), null, "Savings");
        assertThatThrownBy(() -> cash.recordCapital(CapitalType.CAPITAL_WITHDRAWN,
                usdBefore.cash().plus(Money.of("1500", "USD")), null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("in cash");

        cash.exchange(Money.of("300", "USD"), Money.of("900", "TND"), null, "Exchange office");

        Category watches = catalog.createCategory("Cash watches", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product watch = catalog.createProduct(watches.id(), "CASH-W1", Map.of("modell", "Diver 40"));
        Location dubai = stock.createLocation("Dubai storage", "United Arab Emirates");
        Supplier supplier = purchases.createSupplier("Cash watch supplier", "UAE", null, null);
        Purchase purchase = purchases.createPurchase(supplier.id(), null, "USD", null,
                List.of(new NewPurchaseLine(watch.id(), dubai.id(), 2, "100", null)));
        payments.recordPurchasePayment(purchase.publicId(), Money.of("200", "USD"), null, PaymentMethod.BANK_TRANSFER,
                null);

        Sale sale = sales.createSale(null, dubai.id(), null, "USD", null,
                List.of(new NewSaleLine(null, watch.id(), 1, "180", null, null)));
        payments.recordSalePayment(sale.publicId(), Money.of("100", "USD"), null, PaymentMethod.CASH, null);
        expenses.addExpense(ExpenseCategory.PACKAGING, Money.of("10", "USD"), null, null, null, sale.publicId());
        cash.recordCapital(CapitalType.PROFIT_WITHDRAWN, Money.of("50", "USD"), null, null);

        CurrencyPosition usd = position("USD");
        // +1000 capital, -300 exchanged, -200 supplier, +100 customer, -10 packaging, -50 profit taken
        assertThat(usd.cash()).isEqualTo(usdBefore.cash().plus(Money.of("540", "USD")));
        assertThat(position("TND").cash()).isEqualTo(tndBefore.cash().plus(Money.of("900", "TND")));
        // One watch of 100 USD still in stock, 80 USD still owed by the customer.
        assertThat(usd.stockValue()).isEqualTo(usdBefore.stockValue().plus(Money.of("100", "USD")));
        assertThat(usd.receivables()).isEqualTo(usdBefore.receivables().plus(Money.of("80", "USD")));
        assertThat(usd.payables()).isEqualTo(usdBefore.payables());
        assertThat(usd.businessValue()).isEqualTo(usdBefore.businessValue().plus(Money.of("720", "USD")));
        assertThat(profitWithdrawn("USD")).isEqualTo(capitalBefore.plus(Money.of("50", "USD")));

        assertThat(cash.cashBook())
                .anyMatch(m -> m.type().equals("SUPPLIER_PAYMENT") && purchase.publicId().equals(m.reference())
                        && m.amount().equals(Money.of("-200", "USD")))
                .anyMatch(m -> m.type().equals("SALE_PAYMENT") && sale.publicId().equals(m.reference()));
        assertThatThrownBy(() -> cash.exchange(Money.of("5", "USD"), Money.of("5", "USD"), null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    private CurrencyPosition position(String currency) {
        Money zero = Money.zero(currency);
        return cash.overview().stream()
                .filter(p -> p.currency().equals(currency))
                .findFirst()
                .orElse(new CurrencyPosition(currency, zero, zero, zero, zero, zero));
    }

    private Money profitWithdrawn(String currency) {
        return cash.capital().stream()
                .filter(s -> s.currency().equals(currency))
                .map(CapitalSummary::profitWithdrawn)
                .findFirst()
                .orElse(Money.of(BigDecimal.ZERO.toPlainString(), currency));
    }
}
