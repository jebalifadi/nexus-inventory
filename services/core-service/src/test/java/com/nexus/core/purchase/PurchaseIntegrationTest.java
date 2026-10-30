package com.nexus.core.purchase;

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
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.stock.InventoryUnit;
import com.nexus.core.stock.Location;
import com.nexus.core.stock.StockService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Buys phones and sneakers from a supplier and checks the stock they create, on a real PostgreSQL. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class PurchaseIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private PurchaseService purchases;

    @Test
    void aPurchasePutsItsGoodsIntoStockOrNothingAtAll() {
        Category phones = catalog.createCategory("Purchase phones", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null),
                new FieldDefinition("Akku %", FieldType.NUMBER, FieldScope.ITEM, true, null),
                new FieldDefinition("IMEI", FieldType.TEXT, FieldScope.ITEM, false, null)));
        Category sneakers = catalog.createCategory("Purchase sneakers", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product iphone = catalog.createProduct(phones.id(), "PUR-IP15", Map.of("modell", "iPhone 15"));
        Product airMax = catalog.createProduct(sneakers.id(), "PUR-AM95", Map.of("modell", "Air Max 95"));
        Location istanbul = stock.createLocation("Istanbul storage", "Turkey");
        Supplier supplier = purchases.createSupplier("Shenzhen Mobile Co", "China", null, null);

        Purchase purchase = purchases.createPurchase(supplier.id(), LocalDate.of(2026, 10, 20), "EUR", null,
                List.of(
                        new NewPurchaseLine(iphone.id(), istanbul.id(), null, "400", List.of(
                                Map.of("akku", "95", "imei", "350000000000001"),
                                Map.of("akku", "88"))),
                        new NewPurchaseLine(airMax.id(), istanbul.id(), 10, "35.50", null)));

        assertThat(purchase.publicId()).startsWith("P-");
        assertThat(purchase.supplierName()).isEqualTo("Shenzhen Mobile Co");
        assertThat(purchase.total()).isEqualTo(Money.of("1155.00", "EUR"));
        assertThat(purchase.lines()).hasSize(2);

        PurchaseLine phoneLine = purchase.lines().get(0);
        assertThat(phoneLine.quantity()).isEqualTo(2);
        assertThat(phoneLine.unitIds()).hasSize(2).allMatch(id -> id.startsWith("U-"));
        InventoryUnit firstPhone = stock.getUnit(phoneLine.unitIds().get(0));
        assertThat(firstPhone.unitCost()).isEqualTo(Money.of("400.00", "EUR"));
        assertThat(firstPhone.receivedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(firstPhone.attributes()).containsEntry("akku", "95");

        PurchaseLine sneakerLine = purchase.lines().get(1);
        assertThat(sneakerLine.lotId()).startsWith("LOT-");
        assertThat(sneakerLine.lineTotal()).isEqualTo(Money.of("355.00", "EUR"));
        assertThat(stock.getLot(sneakerLine.lotId()).quantityRemaining()).isEqualTo(10);

        // Line 2 is wrong (no battery value), so line 1 must not be saved either.
        int purchasesBefore = purchases.listPurchases().size();
        assertThatThrownBy(() -> purchases.createPurchase(supplier.id(), null, "EUR", null, List.of(
                new NewPurchaseLine(airMax.id(), istanbul.id(), 5, "30", null),
                new NewPurchaseLine(iphone.id(), istanbul.id(), 1, "390", null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("Line 2:");
        assertThat(purchases.listPurchases()).hasSize(purchasesBefore);
        assertThat(stock.stockLevels()).anySatisfy(level -> {
            assertThat(level.productId()).isEqualTo(airMax.id());
            assertThat(level.quantity()).isEqualTo(10);
        });
    }

    @Test
    void refusesLinesThatDoNotAddUp() {
        Category phones = catalog.createCategory("Purchase tablets", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product ipad = catalog.createProduct(phones.id(), "PUR-IPAD", Map.of("modell", "iPad Air"));
        Location izmir = stock.createLocation("Izmir storage", "Turkey");
        Supplier supplier = purchases.createSupplier("Ankara Trade", "Turkey", null, null);

        assertThatThrownBy(() -> purchases.createPurchase(supplier.id(), null, "EUR", null, List.of(
                new NewPurchaseLine(ipad.id(), izmir.id(), 3, "300", List.of(Map.of(), Map.of())))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("quantity is 3 but 2 items were given");
        assertThatThrownBy(() -> purchases.createPurchase(supplier.id(), null, "EUR", null, List.of(
                new NewPurchaseLine(ipad.id(), izmir.id(), 1, "-5", null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be negative");
        assertThatThrownBy(() -> purchases.createPurchase(supplier.id(), null, "EUR", null, List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one line");
    }
}
