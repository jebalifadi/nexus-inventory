package com.nexus.core.sale;

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
import com.nexus.core.shipment.AllocationMethod;
import com.nexus.core.shipment.Shipment;
import com.nexus.core.shipment.ShipmentService;
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

/** Sells phones and sneakers and checks cost, profit, FIFO and warranty, on a real PostgreSQL. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class SaleIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private ShipmentService shipments;

    @Autowired
    private SaleService sales;

    @Test
    void sellsAShippedPhoneWithItsRealCostProfitAndWarranty() {
        Product phone = product("Sale phones", TrackingMode.UNIT, "SALE-IP12");
        Location lyon = stock.createLocation("Lyon storage", "France");
        Location munich = stock.createLocation("Munich shop", "Germany");
        InventoryUnit unit = stock.receiveUnit(phone.id(), lyon.id(), Money.of("300", "EUR"), null, Map.of());
        Shipment shipment = shipments.createShipment(lyon.id(), munich.id(), "DPD", null, Money.of("20", "EUR"),
                AllocationMethod.BY_VALUE, LocalDate.of(2026, 10, 1), List.of(unit.publicId()), null);
        shipments.markArrived(shipment.publicId(), LocalDate.of(2026, 10, 3));
        Customer amira = sales.createCustomer("Amira Ben Salah", "+49 170 0000000", null, "Germany", null);

        Sale sale = sales.createSale(amira.id(), munich.id(), LocalDate.of(2026, 10, 10), "EUR", null, List.of(
                new NewSaleLine(unit.publicId(), null, null, "450", 90, "Battery and screen")));

        assertThat(sale.publicId()).startsWith("S-");
        assertThat(sale.customerName()).isEqualTo("Amira Ben Salah");
        SaleLine line = sale.lines().get(0);
        assertThat(line.unitId()).isEqualTo(unit.publicId());
        assertThat(line.cost()).isEqualTo(Money.of("320.00", "EUR"));
        assertThat(line.profit()).isEqualTo(Money.of("130.00", "EUR"));
        assertThat(line.warrantyEndDate()).isEqualTo(LocalDate.of(2027, 1, 8));
        assertThat(sale.total()).isEqualTo(Money.of("450.00", "EUR"));
        assertThat(sale.profit()).isEqualTo(Money.of("130.00", "EUR"));
        assertThat(stock.getUnit(unit.publicId()).status()).isEqualTo(UnitStatus.SOLD);

        assertThatThrownBy(() -> sales.createSale(null, munich.id(), null, "EUR", null, List.of(
                new NewSaleLine(unit.publicId(), null, null, "450", 0, null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Line 1:")
                .hasMessageContaining("SOLD");
        assertThatThrownBy(() -> sales.createSale(null, munich.id(), null, "EUR", null, List.of(
                new NewSaleLine(null, phone.id(), 1, "450", 0, null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("choose which unit");
    }

    @Test
    void sellsSneakersFromTheOldestLotsFirst() {
        Product sneaker = product("Sale sneakers", TrackingMode.LOT, "SALE-AM97");
        Location paris = stock.createLocation("Paris shop", "France");
        InventoryLot lotA = stock.receiveLot(sneaker.id(), paris.id(), 4, Money.of("40", "EUR"),
                LocalDate.of(2026, 9, 1));
        InventoryLot lotB = stock.receiveLot(sneaker.id(), paris.id(), 10, Money.of("45", "EUR"),
                LocalDate.of(2026, 9, 15));

        Sale sale = sales.createSale(null, paris.id(), LocalDate.of(2026, 10, 12), "EUR", null, List.of(
                new NewSaleLine(null, sneaker.id(), 6, "70", null, null)));

        SaleLine line = sale.lines().get(0);
        assertThat(line.allocations()).extracting(SaleAllocation::lotId, SaleAllocation::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(lotA.publicId(), 4),
                        org.assertj.core.groups.Tuple.tuple(lotB.publicId(), 2));
        assertThat(line.cost()).isEqualTo(Money.of("250.00", "EUR"));
        assertThat(line.lineTotal()).isEqualTo(Money.of("420.00", "EUR"));
        assertThat(line.profit()).isEqualTo(Money.of("170.00", "EUR"));
        assertThat(line.warrantyEndDate()).isNull();
        assertThat(stock.getLot(lotA.publicId()).status()).isEqualTo("DEPLETED");
        assertThat(stock.getLot(lotB.publicId()).quantityRemaining()).isEqualTo(8);

        // Line 1 is fine but line 2 asks for too much: nothing is sold.
        assertThatThrownBy(() -> sales.createSale(null, paris.id(), null, "EUR", null, List.of(
                new NewSaleLine(null, sneaker.id(), 2, "70", null, null),
                new NewSaleLine(null, sneaker.id(), 9, "70", null, null))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Line 2:")
                .hasMessageContaining("Only 6");
        assertThat(stock.getLot(lotB.publicId()).quantityRemaining()).isEqualTo(8);
        assertThatThrownBy(() -> stock.deleteLot(lotA.publicId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("has sales");
    }

    private Product product(String categoryName, TrackingMode mode, String sku) {
        Category category = catalog.createCategory(categoryName, mode, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        return catalog.createProduct(category.id(), sku, Map.of("modell", sku));
    }
}
