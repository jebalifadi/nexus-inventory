package com.nexus.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;

/** Stores and reads the catalog in a disposable PostgreSQL container. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class CatalogIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Test
    void storesOwnerDefinedCategoryAndProduct() {
        Category phones = catalog.createCategory("Phones", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null),
                new FieldDefinition("Speicher", FieldType.CHOICE, FieldScope.PRODUCT, true,
                        List.of("64 GB", "128 GB", "256 GB")),
                new FieldDefinition("Farbe", FieldType.TEXT, FieldScope.PRODUCT, false, null),
                new FieldDefinition("Akku %", FieldType.NUMBER, FieldScope.ITEM, false, null),
                new FieldDefinition("IMEI", FieldType.TEXT, FieldScope.ITEM, false, null)));

        assertThat(phones.fields()).extracting(CategoryField::key)
                .containsExactly("modell", "speicher", "farbe", "akku", "imei");
        assertThat(phones.field("speicher").orElseThrow().choices()).containsExactly("64 GB", "128 GB", "256 GB");

        Product product = catalog.createProduct(phones.id(), "IP14P-128-BLK",
                Map.of("modell", "iPhone 14 Pro", "speicher", "128 GB", "farbe", "Schwarz"));

        assertThat(product.name()).isEqualTo("iPhone 14 Pro, 128 GB, Schwarz");
        assertThat(catalog.getProduct(product.id()).attributes())
                .containsExactly(Map.entry("modell", "iPhone 14 Pro"), Map.entry("speicher", "128 GB"),
                        Map.entry("farbe", "Schwarz"));
        assertThat(catalog.listProducts(phones.id())).hasSize(1);
    }

    @Test
    void rejectsDuplicateSku() {
        Category sneakers = catalog.createCategory("Sneakers", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        catalog.createProduct(sneakers.id(), "AM90-42", Map.of("modell", "Air Max 90"));

        assertThatThrownBy(() -> catalog.createProduct(sneakers.id(), "AM90-42", Map.of("modell", "Air Max 90")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void renamesCategoryAndKeepsItsFields() {
        Category tablets = catalog.createCategory("Tablets", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null),
                new FieldDefinition("IMEI", FieldType.TEXT, FieldScope.ITEM, false, null)));

        Category renamed = catalog.renameCategory(tablets.id(), "  iPads  ");

        assertThat(renamed.name()).isEqualTo("iPads");
        assertThat(renamed.trackingMode()).isEqualTo(TrackingMode.UNIT);
        assertThat(renamed.fields()).extracting(CategoryField::key).containsExactly("modell", "imei");
        assertThatThrownBy(() -> catalog.renameCategory(tablets.id(), " "))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsRenamingCategoryToAnExistingName() {
        catalog.createCategory("Watches", TrackingMode.LOT, List.of());
        Category clocks = catalog.createCategory("Clocks", TrackingMode.LOT, List.of());

        assertThatThrownBy(() -> catalog.renameCategory(clocks.id(), "Watches"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void updatesProductSkuAndValuesAndRebuildsTheName() {
        Category laptops = catalog.createCategory("Laptops", TrackingMode.UNIT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null),
                new FieldDefinition("RAM", FieldType.CHOICE, FieldScope.PRODUCT, false, List.of("8 GB", "16 GB")),
                new FieldDefinition("Farbe", FieldType.TEXT, FieldScope.PRODUCT, false, null)));
        Product product = catalog.createProduct(laptops.id(), "MBA-8",
                Map.of("modell", "MacBook Air", "ram", "8 GB", "farbe", "Silber"));

        // Change the SKU and the RAM, and clear the optional colour.
        Product updated = catalog.updateProduct(product.id(), "MBA-16",
                Map.of("modell", "MacBook Air", "ram", "16 GB"));

        assertThat(updated.sku()).isEqualTo("MBA-16");
        assertThat(updated.name()).isEqualTo("MacBook Air, 16 GB");
        assertThat(catalog.getProduct(product.id()).attributes())
                .containsExactly(Map.entry("modell", "MacBook Air"), Map.entry("ram", "16 GB"));
    }

    @Test
    void checksProductChangesLikeANewProduct() {
        Category consoles = catalog.createCategory("Consoles", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product ps5 = catalog.createProduct(consoles.id(), "PS5", Map.of("modell", "PlayStation 5"));
        catalog.createProduct(consoles.id(), "XSX", Map.of("modell", "Xbox Series X"));

        assertThatThrownBy(() -> catalog.updateProduct(ps5.id(), "PS5", Map.of()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("required");
        assertThatThrownBy(() -> catalog.updateProduct(ps5.id(), "PS 5", Map.of("modell", "PlayStation 5")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("SKU");
        assertThatThrownBy(() -> catalog.updateProduct(ps5.id(), "XSX", Map.of("modell", "PlayStation 5")))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(catalog.getProduct(ps5.id()).sku()).isEqualTo("PS5");
    }

    @Test
    void cannotUpdateADeletedProduct() {
        Category drones = catalog.createCategory("Drones", TrackingMode.LOT, List.of(
                new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null)));
        Product mini = catalog.createProduct(drones.id(), "MINI4", Map.of("modell", "Mini 4"));
        catalog.deleteProduct(mini.id(), "test");

        assertThatThrownBy(() -> catalog.updateProduct(mini.id(), "MINI4", Map.of("modell", "Mini 4 Pro")))
                .isInstanceOf(NotFoundException.class);
    }
}
