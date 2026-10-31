package com.nexus.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.shared.BusinessRuleException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogRulesTest {

    private static final Category PHONES = new Category(1L, "Phones", TrackingMode.UNIT, CategoryRules.validate(
            "Phones", TrackingMode.UNIT, List.of(
                    new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null),
                    new FieldDefinition("Speicher", FieldType.CHOICE, FieldScope.PRODUCT, true,
                            List.of("64 GB", "128 GB", "256 GB")),
                    new FieldDefinition("Farbe", FieldType.TEXT, FieldScope.PRODUCT, false, null),
                    new FieldDefinition("Akku %", FieldType.NUMBER, FieldScope.ITEM, false, null))));

    @Test
    void derivesStableKeysFromLabels() {
        assertThat(FieldKeys.fromLabel("Speicher")).isEqualTo("speicher");
        assertThat(FieldKeys.fromLabel("Akku %")).isEqualTo("akku");
        assertThat(FieldKeys.fromLabel("Größe")).isEqualTo("grosse");
    }

    @Test
    void choiceFieldNeedsChoices() {
        assertThatThrownBy(() -> CategoryRules.validate("Phones", TrackingMode.UNIT, List.of(
                new FieldDefinition("Speicher", FieldType.CHOICE, FieldScope.PRODUCT, true, List.of()))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("needs at least one choice");
    }

    @Test
    void withNoNameFieldMarkedEveryProductFieldFormsTheName() {
        Map<String, String> values = ProductRules.validateAttributes(PHONES,
                Map.of("farbe", "Schwarz", "modell", "iPhone 14 Pro", "speicher", "128 GB"));

        assertThat(ProductRules.buildName(PHONES, values)).isEqualTo("iPhone 14 Pro · 128 GB · Schwarz");
    }

    @Test
    void onlyMarkedFieldsFormTheName() {
        Category phones = new Category(2L, "Phones", TrackingMode.UNIT, CategoryRules.validate(
                "Phones", TrackingMode.UNIT, List.of(
                        new FieldDefinition("Modell", FieldType.TEXT, FieldScope.PRODUCT, true, null, true),
                        new FieldDefinition("Farbe", FieldType.TEXT, FieldScope.PRODUCT, false, null),
                        new FieldDefinition("Speicher", FieldType.TEXT, FieldScope.PRODUCT, true, null, true),
                        new FieldDefinition("IMEI", FieldType.TEXT, FieldScope.ITEM, false, null))));

        assertThat(phones.nameFields()).extracting(CategoryField::key).containsExactly("modell", "speicher");
        assertThat(ProductRules.buildName(phones, Map.of("modell", "iPhone 13", "farbe", "Blau", "speicher", "128 GB")))
                .isEqualTo("iPhone 13 · 128 GB");
    }

    @Test
    void aPerPieceFieldCannotBePartOfTheName() {
        assertThatThrownBy(() -> CategoryRules.validate("Phones", TrackingMode.UNIT, List.of(
                new FieldDefinition("IMEI", FieldType.TEXT, FieldScope.ITEM, false, null, true))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be part of the product name");
        assertThatThrownBy(() -> CategoryRules.validateNameFields(PHONES, List.of("modell", "akku")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be part of the product name");
        assertThatThrownBy(() -> CategoryRules.validateNameFields(PHONES, List.of("modell", "modell")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void rejectsValueOutsideTheOwnersChoices() {
        assertThatThrownBy(() -> ProductRules.validateAttributes(PHONES,
                Map.of("modell", "iPhone 14 Pro", "speicher", "512 GB")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must be one of: 64 GB, 128 GB, 256 GB");
    }

    @Test
    void rejectsMissingRequiredField() {
        assertThatThrownBy(() -> ProductRules.validateAttributes(PHONES, Map.of("speicher", "64 GB")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("'Modell' is required");
    }

    @Test
    void rejectsItemFieldOnProduct() {
        assertThatThrownBy(() -> ProductRules.validateAttributes(PHONES,
                Map.of("modell", "iPhone 14 Pro", "speicher", "64 GB", "akku", "91")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("filled in for each item");
    }
}
