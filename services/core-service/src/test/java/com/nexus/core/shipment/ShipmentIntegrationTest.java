package com.nexus.core.shipment;

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

/** Ships goods between two locations and checks stock and landed cost, on a real PostgreSQL. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class ShipmentIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private ShipmentService shipments;

    @Test
    void shipsTwoPhonesAndSplitsTheCostByValue() {
        Product phone = product("Ship phones", TrackingMode.UNIT, "SHIP-IP14");
        Location guangzhou = stock.createLocation("Guangzhou storage", "China");
        Location tunis = stock.createLocation("Tunis warehouse", "Tunisia");
        InventoryUnit expensive = stock.receiveUnit(phone.id(), guangzhou.id(), Money.of("300", "EUR"), null, Map.of());
        InventoryUnit cheap = stock.receiveUnit(phone.id(), guangzhou.id(), Money.of("100", "EUR"), null, Map.of());

        Shipment shipment = shipments.createShipment(guangzhou.id(), tunis.id(), "DHL", "JD0145", Money.of("60", "EUR"),
                AllocationMethod.BY_VALUE, LocalDate.of(2026, 10, 1),
                List.of(expensive.publicId(), cheap.publicId()), null);

        assertThat(shipment.publicId()).startsWith("SH-");
        assertThat(shipment.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(shipment.items()).extracting(ShipmentItem::allocatedCost)
                .containsExactly(Money.of("45.00", "EUR"), Money.of("15.00", "EUR"));
        assertThat(shipment.items()).extracting(ShipmentItem::landedUnitCost)
                .containsExactly(Money.of("345.00", "EUR"), Money.of("115.00", "EUR"));
        assertThat(stock.getUnit(cheap.publicId()).status()).isEqualTo(UnitStatus.IN_TRANSIT);
        assertThat(stock.stockLevels()).noneMatch(level -> level.productId().equals(phone.id()));

        Shipment arrived = shipments.markArrived(shipment.publicId(), LocalDate.of(2026, 10, 9));

        assertThat(arrived.status()).isEqualTo(ShipmentStatus.ARRIVED);
        assertThat(arrived.arrivedDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        InventoryUnit received = stock.getUnit(cheap.publicId());
        assertThat(received.status()).isEqualTo(UnitStatus.IN_INVENTORY);
        assertThat(received.locationId()).isEqualTo(tunis.id());
        assertThat(stock.stockLevels()).anySatisfy(level -> {
            assertThat(level.productId()).isEqualTo(phone.id());
            assertThat(level.locationName()).isEqualTo("Tunis warehouse");
            assertThat(level.quantity()).isEqualTo(2);
        });
        assertThatThrownBy(() -> shipments.markArrived(shipment.publicId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already arrived");
        assertThatThrownBy(() -> shipments.createShipment(guangzhou.id(), tunis.id(), null, null,
                Money.zero("EUR"), null, null, List.of(cheap.publicId()), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is not at location");
    }

    @Test
    void mixedCurrenciesAreSplitByQuantityAndLotsTravelToo() {
        Product tablet = product("Ship tablets", TrackingMode.UNIT, "SHIP-IPAD");
        Product sneaker = product("Ship sneakers", TrackingMode.LOT, "SHIP-AM90");
        Location antalya = stock.createLocation("Antalya storage", "Turkey");
        Location sfax = stock.createLocation("Sfax warehouse", "Tunisia");
        InventoryUnit euroTablet = stock.receiveUnit(tablet.id(), antalya.id(), Money.of("200", "EUR"), null, Map.of());
        InventoryUnit dinarTablet = stock.receiveUnit(tablet.id(), antalya.id(), Money.of("650", "TND"), null, Map.of());
        InventoryLot lot = stock.receiveLot(sneaker.id(), antalya.id(), 4, Money.of("90", "TND"), null);
        List<String> units = List.of(euroTablet.publicId(), dinarTablet.publicId());

        assertThatThrownBy(() -> shipments.createShipment(antalya.id(), sfax.id(), null, null, Money.of("25", "EUR"),
                AllocationMethod.BY_VALUE, null, units, List.of(lot.publicId())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("use BY_QUANTITY");
        // The refused shipment changed nothing: the tablet is still in stock.
        assertThat(stock.getUnit(euroTablet.publicId()).status()).isEqualTo(UnitStatus.IN_INVENTORY);

        Shipment shipment = shipments.createShipment(antalya.id(), sfax.id(), null, null, Money.of("25", "EUR"),
                AllocationMethod.BY_QUANTITY, null, units, List.of(lot.publicId()));

        assertThat(shipment.items()).extracting(ShipmentItem::allocatedCost).containsExactly(
                Money.of("4.17", "EUR"), Money.of("4.17", "EUR"), Money.of("16.66", "EUR"));
        assertThat(shipment.items().get(0).landedUnitCost()).isEqualTo(Money.of("204.17", "EUR"));
        assertThat(shipment.items().get(1).landedUnitCost()).isNull();
        assertThat(stock.getLot(lot.publicId()).status()).isEqualTo("IN_TRANSIT");
        assertThatThrownBy(() -> stock.deleteLot(lot.publicId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("on its way");

        shipments.markArrived(shipment.publicId(), null);

        InventoryLot arrivedLot = stock.getLot(lot.publicId());
        assertThat(arrivedLot.status()).isEqualTo("IN_STOCK");
        assertThat(arrivedLot.locationId()).isEqualTo(sfax.id());
    }

    private Product product(String categoryName, TrackingMode mode, String sku) {
        Category category = catalog.createCategory(categoryName, mode, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        return catalog.createProduct(category.id(), sku, Map.of("modell", sku));
    }
}
