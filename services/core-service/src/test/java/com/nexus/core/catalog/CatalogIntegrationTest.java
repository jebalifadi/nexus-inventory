package com.nexus.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
