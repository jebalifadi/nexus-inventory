package com.nexus.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.catalog.CatalogService;
import com.nexus.core.catalog.Category;
import com.nexus.core.catalog.FieldDefinition;
import com.nexus.core.catalog.FieldScope;
import com.nexus.core.catalog.FieldType;
import com.nexus.core.catalog.Product;
import com.nexus.core.catalog.TrackingMode;
import com.nexus.core.money.Money;
import com.nexus.core.purchase.NewPurchaseLine;
import com.nexus.core.purchase.Purchase;
import com.nexus.core.purchase.PurchaseService;
import com.nexus.core.purchase.Supplier;
import com.nexus.core.sale.Customer;
import com.nexus.core.sale.NewSaleLine;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.PaymentStatus;
import com.nexus.core.stock.Location;
import com.nexus.core.stock.StockService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Pays a sale in two parts and a purchase in one, on a real PostgreSQL. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class PaymentIntegrationTest {

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

    @Test
    void aSaleIsPaidInPartsAndLeavesTheReceivables() {
        Category category = catalog.createCategory("Pay sneakers", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product sneaker = catalog.createProduct(category.id(), "PAY-AM1", Map.of("modell", "Air Max 1"));
        Location market = stock.createLocation("Tunis market", "Tunisia");
        stock.receiveLot(sneaker.id(), market.id(), 10, Money.of("300", "TND"), null);
        Customer karim = sales.createCustomer("Karim Trabelsi", null, null, "Tunisia", null);
        Sale sale = sales.createSale(karim.id(), market.id(), LocalDate.of(2026, 10, 15), "TND", null,
                List.of(new NewSaleLine(null, sneaker.id(), 2, "700", null, null)));

        assertThat(sale.total()).isEqualTo(Money.of("1400", "TND"));
        assertThat(sale.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.receivables()).anyMatch(open -> open.reference().equals(sale.publicId()));

        Payment first = payments.recordSalePayment(sale.publicId(), Money.of("500", "TND"),
                LocalDate.of(2026, 10, 20), PaymentMethod.CASH, null);

        assertThat(first.publicId()).startsWith("PAY-");
        Sale partlyPaid = sales.getSale(sale.publicId());
        assertThat(partlyPaid.paymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(partlyPaid.balance()).isEqualTo(Money.of("900", "TND"));

        assertThatThrownBy(() -> payments.recordSalePayment(sale.publicId(), Money.of("1000", "TND"), null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("more than the open balance");
        assertThatThrownBy(() -> payments.recordSalePayment(sale.publicId(), Money.of("50", "EUR"), null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be in TND");

        payments.recordSalePayment(sale.publicId(), Money.of("900", "TND"), LocalDate.of(2026, 10, 22),
                PaymentMethod.BANK_TRANSFER, null);

        Sale paid = sales.getSale(sale.publicId());
        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.balance()).isEqualTo(Money.zero("TND"));
        assertThat(payments.paymentsOfSale(sale.publicId())).hasSize(2);
        assertThat(payments.receivables()).noneMatch(open -> open.reference().equals(sale.publicId()));
    }

    @Test
    void aPurchaseStaysPayableUntilTheSupplierIsPaid() {
        Category category = catalog.createCategory("Pay perfume", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product perfume = catalog.createProduct(category.id(), "PAY-PF1", Map.of("modell", "Eau de Parfum"));
        Location storage = stock.createLocation("Bremen storage", "Germany");
        Supplier supplier = purchases.createSupplier("Pay supplier GmbH", "Germany", null, null);
        Purchase purchase = purchases.createPurchase(supplier.id(), null, "EUR", null,
                List.of(new NewPurchaseLine(perfume.id(), storage.id(), 3, "20", null)));

        assertThat(purchase.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payments.payables()).anySatisfy(open -> {
            assertThat(open.reference()).isEqualTo(purchase.publicId());
            assertThat(open.counterparty()).isEqualTo("Pay supplier GmbH");
            assertThat(open.balance()).isEqualTo(Money.of("60", "EUR"));
        });

        payments.recordPurchasePayment(purchase.publicId(), Money.of("60", "EUR"), null, PaymentMethod.BANK_TRANSFER,
                "Invoice 2026-118");

        assertThat(purchases.getPurchase(purchase.publicId()).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payments.payables()).noneMatch(open -> open.reference().equals(purchase.publicId()));
    }
}
