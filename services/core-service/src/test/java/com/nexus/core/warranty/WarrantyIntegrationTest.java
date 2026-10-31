package com.nexus.core.warranty;

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
import com.nexus.core.payment.PaymentMethod;
import com.nexus.core.payment.PaymentService;
import com.nexus.core.sale.NewSaleLine;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.stock.InventoryLot;
import com.nexus.core.stock.InventoryUnit;
import com.nexus.core.stock.Location;
import com.nexus.core.stock.StockService;
import com.nexus.core.stock.UnitStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Warranty claims: a repair, a goodwill refund and a partial return of lot goods, on a real PostgreSQL. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class WarrantyIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private SaleService sales;

    @Autowired
    private PaymentService payments;

    @Autowired
    private WarrantyService warranty;

    @Test
    void aRepairLowersTheProfitAndAGoodwillRefundBringsThePhoneBack() {
        Product phone = product("Claim phones", TrackingMode.UNIT, "WC-IP11");
        Location rome = stock.createLocation("Rome shop", "Italy");
        InventoryUnit unit = stock.receiveUnit(phone.id(), rome.id(), Money.of("300", "EUR"), null, Map.of());
        Sale sale = sales.createSale(null, rome.id(), LocalDate.of(2026, 10, 1), "EUR", null,
                List.of(new NewSaleLine(unit.publicId(), null, null, "500", 30, "Screen and battery")));
        payments.recordSalePayment(sale.publicId(), Money.of("500", "EUR"), null, PaymentMethod.CARD, null);

        WarrantyClaim repair = warranty.openClaim(sale.publicId(), 1, null, LocalDate.of(2026, 10, 20),
                "Screen flickers", null);
        assertThat(repair.publicId()).startsWith("WC-");
        assertThat(repair.withinWarranty()).isTrue();
        assertThat(repair.warrantyEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));

        WarrantyClaim repaired = warranty.resolve(repair.publicId(), ClaimResolution.REPAIRED, Money.of("40", "EUR"),
                LocalDate.of(2026, 10, 22), "New screen");
        assertThat(repaired.status()).isEqualTo(ClaimStatus.RESOLVED);
        assertThat(sales.getSale(sale.publicId()).profit()).isEqualTo(Money.of("160", "EUR"));
        assertThatThrownBy(() -> warranty.resolve(repair.publicId(), ClaimResolution.REPAIRED, null, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already RESOLVED");

        // After 31 Oct the warranty is over: only goodwill, with a reason.
        assertThatThrownBy(() -> warranty.openClaim(sale.publicId(), 1, null, LocalDate.of(2026, 11, 15),
                "Battery drains", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ended on 2026-10-31");
        WarrantyClaim goodwill = warranty.openClaim(sale.publicId(), 1, null, LocalDate.of(2026, 11, 15),
                "Battery drains", "Loyal customer");
        assertThat(goodwill.withinWarranty()).isFalse();

        WarrantyClaim refunded = warranty.resolve(goodwill.publicId(), ClaimResolution.REFUNDED, null,
                LocalDate.of(2026, 11, 16), null);

        assertThat(refunded.refund()).isEqualTo(Money.of("500", "EUR"));
        assertThat(stock.getUnit(unit.publicId()).status()).isEqualTo(UnitStatus.IN_INVENTORY);
        Sale afterRefund = sales.getSale(sale.publicId());
        assertThat(afterRefund.lines().get(0).returnedQuantity()).isEqualTo(1);
        assertThat(afterRefund.returns()).isEqualTo(Money.of("500", "EUR"));
        assertThat(afterRefund.paid()).isEqualTo(Money.zero("EUR"));
        assertThat(afterRefund.balance()).isEqualTo(Money.zero("EUR"));
        // No revenue and no goods cost left, only the 40 EUR repair.
        assertThat(afterRefund.profit()).isEqualTo(Money.of("-40", "EUR"));

        // The phone is back in stock and can be sold again.
        Sale resold = sales.createSale(null, rome.id(), LocalDate.of(2026, 11, 20), "EUR", null,
                List.of(new NewSaleLine(unit.publicId(), null, null, "450", 0, null)));
        assertThat(resold.profit()).isEqualTo(Money.of("150", "EUR"));
    }

    @Test
    void returnedPairsGoBackIntoTheirOwnLots() {
        Product sneaker = product("Claim sneakers", TrackingMode.LOT, "WC-AF1");
        Location milan = stock.createLocation("Milan shop", "Italy");
        InventoryLot lotA = stock.receiveLot(sneaker.id(), milan.id(), 4, Money.of("40", "EUR"), LocalDate.of(2026, 9, 1));
        InventoryLot lotB = stock.receiveLot(sneaker.id(), milan.id(), 10, Money.of("45", "EUR"), LocalDate.of(2026, 9, 15));
        Sale sale = sales.createSale(null, milan.id(), LocalDate.of(2026, 10, 5), "EUR", null,
                List.of(new NewSaleLine(null, sneaker.id(), 6, "70", 0, null)));
        payments.recordSalePayment(sale.publicId(), Money.of("200", "EUR"), null, PaymentMethod.CASH, null);

        WarrantyClaim claim = warranty.openClaim(sale.publicId(), 1, 3, LocalDate.of(2026, 10, 7),
                "Sole comes off", "Factory defect");
        WarrantyClaim refunded = warranty.resolve(claim.publicId(), ClaimResolution.REFUNDED, null, null, null);

        // 3 pairs back: the 2 taken from lot B, then 1 from lot A. Refund is capped at the 200 EUR paid.
        assertThat(stock.getLot(lotB.publicId()).quantityRemaining()).isEqualTo(10);
        assertThat(stock.getLot(lotA.publicId()).quantityRemaining()).isEqualTo(1);
        assertThat(stock.getLot(lotA.publicId()).status()).isEqualTo("IN_STOCK");
        assertThat(refunded.refund()).isEqualTo(Money.of("200", "EUR"));
        Sale after = sales.getSale(sale.publicId());
        assertThat(after.returns()).isEqualTo(Money.of("210", "EUR"));
        assertThat(after.balance()).isEqualTo(Money.of("210", "EUR"));
        // 3 pairs kept from lot A at 40 EUR: 210 - 120 = 90 EUR.
        assertThat(after.profit()).isEqualTo(Money.of("90", "EUR"));

        WarrantyClaim rejected = warranty.reject(
                warranty.openClaim(sale.publicId(), 1, 1, null, "Wrong size", "Asked nicely").publicId(), null,
                "Not a defect");
        assertThat(rejected.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThatThrownBy(() -> warranty.openClaim(sale.publicId(), 1, 4, null, "More", "Again"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1 to 3 piece(s)");
    }

    private Product product(String categoryName, TrackingMode mode, String sku) {
        Category category = catalog.createCategory(categoryName, mode, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        return catalog.createProduct(category.id(), sku, Map.of("modell", sku));
    }
}
