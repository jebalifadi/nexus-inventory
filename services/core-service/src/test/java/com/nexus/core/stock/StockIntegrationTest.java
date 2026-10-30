package com.nexus.core.stock;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Receives units and lots and follows an item through its life cycle, on a real PostgreSQL. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class StockIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Test
    void receivesAPhoneAsAUnitAndFollowsItsLifeCycle() {
        Category phones = catalog.createCategory("Stock phones", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null),
                new FieldDefinition("Akku %", FieldType.NUMBER, FieldScope.ITEM, true, null),
                new FieldDefinition("IMEI", FieldType.TEXT, FieldScope.ITEM, false, null)));
        Product iphone = catalog.createProduct(phones.id(), "STOCK-IP14", Map.of("modell", "iPhone 14 Pro"));
        Location berlin = stock.createLocation("Berlin storage", "Germany");

        InventoryUnit unit = stock.receiveUnit(iphone.id(), berlin.id(), Money.of("323.00", "EUR"),
                LocalDate.of(2026, 10, 1), Map.of("akku", "91", "imei", "356789012345678"));

        assertThat(unit.publicId()).startsWith("U-");
        assertThat(unit.status()).isEqualTo(UnitStatus.IN_INVENTORY);
        assertThat(unit.unitCost()).isEqualTo(Money.of("323.00", "EUR"));
        assertThat(unit.attributes()).containsEntry("akku", "91").containsEntry("imei", "356789012345678");
        assertThat(stock.stockLevels()).anySatisfy(level -> {
            assertThat(level.productId()).isEqualTo(iphone.id());
            assertThat(level.quantity()).isEqualTo(1);
        });

        stock.changeUnitStatus(unit.publicId(), UnitStatus.LISTED);
        InventoryUnit sold = stock.changeUnitStatus(unit.publicId(), UnitStatus.SOLD);

        assertThat(sold.status()).isEqualTo(UnitStatus.SOLD);
        assertThat(stock.stockLevels()).noneSatisfy(level -> assertThat(level.productId()).isEqualTo(iphone.id()));
        assertThatThrownBy(() -> stock.changeUnitStatus(unit.publicId(), UnitStatus.LISTED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot go from SOLD to LISTED");
    }

    @Test
    void receivesSneakersAsALotAndRefusesTheWrongTrackingMode() {
        Category sneakers = catalog.createCategory("Stock sneakers", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product airMax = catalog.createProduct(sneakers.id(), "STOCK-AM90-42", Map.of("modell", "Air Max 90"));
        Location tunis = stock.createLocation("Tunis shop", "Tunisia");

        InventoryLot lot = stock.receiveLot(airMax.id(), tunis.id(), 10, Money.of("40.00", "EUR"),
                LocalDate.of(2026, 10, 2));

        assertThat(lot.publicId()).startsWith("LOT-");
        assertThat(lot.quantityRemaining()).isEqualTo(10);
        assertThat(stock.stockLevels()).anySatisfy(level -> {
            assertThat(level.productId()).isEqualTo(airMax.id());
            assertThat(level.locationName()).isEqualTo("Tunis shop");
            assertThat(level.quantity()).isEqualTo(10);
        });
        assertThatThrownBy(() -> stock.receiveUnit(airMax.id(), tunis.id(), Money.of("40", "EUR"), null, Map.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("receive a lot instead");
        assertThatThrownBy(() -> stock.receiveLot(airMax.id(), tunis.id(), 5, Money.of("-1", "EUR"), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be negative");
    }
}
