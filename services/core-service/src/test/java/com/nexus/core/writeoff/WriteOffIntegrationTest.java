package com.nexus.core.writeoff;

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
import com.nexus.core.sale.NewSaleLine;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shipment.AllocationMethod;
import com.nexus.core.shipment.Shipment;
import com.nexus.core.shipment.ShipmentService;
import com.nexus.core.stock.InventoryLot;
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

/** Damaged or lost goods: their real cost becomes a loss, and restoring them cancels it. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class WriteOffIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private ShipmentService shipments;

    @Autowired
    private ExpenseService expenses;

    @Autowired
    private SaleService sales;

    @Autowired
    private WriteOffService writeOffs;

    @Test
    void aDamagedPhoneLosesItsPurchaseShippingAndRepairCost() {
        Category phones = catalog.createCategory("WO phones", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product iphone = catalog.createProduct(phones.id(), "WO-IP13", Map.of("modell", "iPhone 13"));
        Location berlin = stock.createLocation("WO Berlin", "Germany");
        Location tunis = stock.createLocation("WO Tunis", "Tunisia");
        InventoryUnit phone = stock.receiveUnit(iphone.id(), berlin.id(), Money.of("300", "EUR"), DAY, Map.of());

        // Shipped alone for 20 EUR, then repaired for 15 EUR: its real cost is 335 EUR.
        Shipment shipment = shipments.createShipment(berlin.id(), tunis.id(), null, null, Money.of("20", "EUR"),
                AllocationMethod.BY_QUANTITY, DAY, List.of(phone.publicId()), List.of());
        shipments.markArrived(shipment.publicId(), DAY.plusDays(3));
        expenses.addExpense(ExpenseCategory.REPAIR, Money.of("15", "EUR"), DAY.plusDays(4), "Screen",
                phone.publicId(), null);

        WriteOff writeOff = writeOffs.writeOffUnit(phone.publicId(), "Damaged", DAY.plusDays(10));

        assertThat(writeOff.publicId()).startsWith("WO-");
        assertThat(writeOff.unitId()).isEqualTo(phone.publicId());
        assertThat(writeOff.quantity()).isEqualTo(1);
        assertThat(writeOff.reason()).isEqualTo("Damaged");
        assertThat(writeOff.cost()).containsExactly(Money.of("335", "EUR"));
        assertThat(writeOffs.listWriteOffs()).extracting(WriteOff::publicId).contains(writeOff.publicId());
        // The phone left stock like any removal: it is in the trash.
        assertThatThrownBy(() -> stock.getUnit(phone.publicId())).hasMessageContaining("deleted");

        // Found again: restoring it cancels the loss.
        stock.restoreUnit(phone.publicId());
        assertThat(writeOffs.listWriteOffs()).extracting(WriteOff::publicId).doesNotContain(writeOff.publicId());
    }

    @Test
    void aLostBatchLosesWhatIsLeftOfIt() {
        Category cables = catalog.createCategory("WO cables", TrackingMode.LOT, List.of());
        Product usbC = catalog.createProduct(cables.id(), "WO-USBC", Map.of());
        Location shop = stock.createLocation("WO shop", "Germany");
        InventoryLot lot = stock.receiveLot(usbC.id(), shop.id(), 10, Money.of("2.50", "EUR"), DAY);

        WriteOff writeOff = writeOffs.writeOffLot(lot.publicId(), "Lost", DAY);

        assertThat(writeOff.lotId()).isEqualTo(lot.publicId());
        assertThat(writeOff.quantity()).isEqualTo(10);
        assertThat(writeOff.cost()).containsExactly(Money.of("25", "EUR"));
    }

    @Test
    void soldGoodsAndBadInputAreRefusedAndNothingIsRecorded() {
        Category phones = catalog.createCategory("WO sold phones", TrackingMode.UNIT, List.of());
        Product phone = catalog.createProduct(phones.id(), "WO-SOLD", Map.of());
        Location shop = stock.createLocation("WO sold shop", "Germany");
        InventoryUnit sold = stock.receiveUnit(phone.id(), shop.id(), Money.of("100", "EUR"), DAY, Map.of());
        InventoryUnit kept = stock.receiveUnit(phone.id(), shop.id(), Money.of("100", "EUR"), DAY, Map.of());
        sales.createSale(null, shop.id(), DAY, "EUR", null,
                List.of(new NewSaleLine(sold.publicId(), null, null, "150", 0, null)));
        int before = writeOffs.listWriteOffs().size();

        assertThatThrownBy(() -> writeOffs.writeOffUnit(sold.publicId(), "Damaged", DAY))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("sold");
        assertThatThrownBy(() -> writeOffs.writeOffUnit(kept.publicId(), " ", DAY))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("why");
        assertThatThrownBy(() -> writeOffs.writeOffUnit(kept.publicId(), "Lost", LocalDate.now().plusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");

        assertThat(writeOffs.listWriteOffs()).hasSize(before);
        assertThat(stock.getUnit(kept.publicId()).publicId()).isEqualTo(kept.publicId());
    }
}
