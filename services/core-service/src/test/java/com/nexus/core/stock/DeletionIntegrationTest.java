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
import com.nexus.core.shared.NotFoundException;
import com.nexus.core.trash.TrashRepository;
import com.nexus.core.trash.TrashType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

/** Soft delete and restore, guarded by business rules, on a real PostgreSQL. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class DeletionIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private StockService stock;

    @Autowired
    private TrashRepository trash;

    @Test
    void soldPhonesStayButStockCanBeDeletedAndRestored() {
        Category phones = catalog.createCategory("Trash phones", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product iphone = catalog.createProduct(phones.id(), "TRASH-IP13", Map.of("modell", "iPhone 13"));
        Location hamburg = stock.createLocation("Hamburg storage", "Germany");
        InventoryUnit sold = stock.receiveUnit(iphone.id(), hamburg.id(), Money.of("250.00", "EUR"),
                LocalDate.of(2026, 10, 1), Map.of());
        InventoryUnit inStock = stock.receiveUnit(iphone.id(), hamburg.id(), Money.of("260.00", "EUR"),
                LocalDate.of(2026, 10, 1), Map.of());
        stock.changeUnitStatus(sold.publicId(), UnitStatus.SOLD);

        assertThatThrownBy(() -> stock.deleteUnit(sold.publicId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is sold and cannot be deleted");
        assertThatThrownBy(() -> stock.deleteLocation(hamburg.id(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still holds 1 item");
        assertThatThrownBy(() -> catalog.deleteProduct(iphone.id(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still has 1 item");

        stock.deleteUnit(inStock.publicId(), "Duplicate entry");

        assertThatThrownBy(() -> stock.getUnit(inStock.publicId()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("is deleted");
        assertThat(stock.listUnits()).noneMatch(unit -> unit.publicId().equals(inStock.publicId()));
        assertThat(stock.stockLevels()).noneMatch(level -> level.productId().equals(iphone.id()));
        assertThat(trash.findAll()).anySatisfy(item -> {
            assertThat(item.type()).isEqualTo(TrashType.UNIT);
            assertThat(item.name()).isEqualTo(inStock.publicId());
            assertThat(item.reason()).isEqualTo("Duplicate entry");
        });

        // Only the sold phone is left, so the product can go. The sold phone itself stays.
        catalog.deleteProduct(iphone.id(), null);
        assertThat(stock.getUnit(sold.publicId()).status()).isEqualTo(UnitStatus.SOLD);
        assertThatThrownBy(() -> stock.restoreUnit(inStock.publicId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("restore product");

        catalog.restoreProduct(iphone.id());
        InventoryUnit restored = stock.restoreUnit(inStock.publicId());

        assertThat(restored.status()).isEqualTo(UnitStatus.IN_INVENTORY);
        assertThat(stock.stockLevels()).anySatisfy(level -> {
            assertThat(level.productId()).isEqualTo(iphone.id());
            assertThat(level.quantity()).isEqualTo(1);
        });
    }

    @Test
    void deletedNamesCanBeReusedAndParentsMustBeEmpty() {
        Category sneakers = catalog.createCategory("Trash sneakers", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product airMax = catalog.createProduct(sneakers.id(), "TRASH-AM1", Map.of("modell", "Air Max 1"));
        Location shop = stock.createLocation("Sousse shop", "Tunisia");
        InventoryLot lot = stock.receiveLot(airMax.id(), shop.id(), 5, Money.of("30.000", "TND"), null);

        assertThatThrownBy(() -> catalog.deleteCategory(sneakers.id(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("still has 1 product");

        stock.deleteLot(lot.publicId(), null);
        stock.deleteLocation(shop.id(), "Shop closed");

        assertThatThrownBy(() -> stock.receiveLot(airMax.id(), shop.id(), 1, Money.of("30", "TND"), null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("is deleted");
        assertThatThrownBy(() -> stock.restoreLot(lot.publicId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("restore location");

        Location newShop = stock.createLocation("Sousse shop", "Tunisia");
        assertThat(newShop.id()).isNotEqualTo(shop.id());
        assertThatThrownBy(() -> stock.restoreLocation(shop.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> stock.restoreLocation(newShop.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is not deleted");
    }
}
