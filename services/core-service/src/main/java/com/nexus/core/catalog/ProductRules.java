package com.nexus.core.catalog;

import com.nexus.core.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Validates product data against the fields its category defines. */
final class ProductRules {

    private static final int MAX_VALUE_LENGTH = 255;

    private ProductRules() {
    }

    static String validateSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new BusinessRuleException("SKU is required");
        }
        String value = sku.trim();
        if (value.length() > 64 || !value.matches("[A-Za-z0-9._-]+")) {
            throw new BusinessRuleException(
                    "SKU may only contain letters, digits, '.', '_' and '-' (max 64 characters)");
        }
        return value;
    }

    /** Validates product-level values; returns them in the category's field order. */
    static Map<String, String> validateAttributes(Category category, Map<String, String> input) {
        return validate(category, input, FieldScope.PRODUCT);
    }

    /** Validates the values of one physical item (for example battery health or IMEI). */
    static Map<String, String> validateItemAttributes(Category category, Map<String, String> input) {
        return validate(category, input, FieldScope.ITEM);
    }

    private static Map<String, String> validate(Category category, Map<String, String> input, FieldScope scope) {
        Map<String, String> values = input == null ? Map.of() : input;
        for (String key : values.keySet()) {
            CategoryField field = category.field(key).orElseThrow(() -> new BusinessRuleException(
                    "Unknown field '" + key + "' for category '" + category.name() + "'"));
            if (field.scope() != scope) {
                throw new BusinessRuleException(scope == FieldScope.PRODUCT
                        ? "Field '" + field.label() + "' is filled in for each item, not for the product"
                        : "Field '" + field.label() + "' is set on the product, not on each item");
            }
        }
        Map<String, String> validated = new LinkedHashMap<>();
        for (CategoryField field : category.fields(scope)) {
            String raw = values.get(field.key());
            if (raw == null || raw.isBlank()) {
                if (field.required()) {
                    throw new BusinessRuleException("Field '" + field.label() + "' is required");
                }
                continue;
            }
            validated.put(field.key(), normalize(field, raw.trim()));
        }
        return validated;
    }

    /** Joins the parts of a product name, for example "iPhone 13 · 128 GB". */
    static final String NAME_SEPARATOR = " · ";

    /** Builds a product name from the category's name fields, in name order; empty when none is filled. */
    static String buildName(Category category, Map<String, String> attributes) {
        return category.nameFields().stream()
                .map(field -> attributes.get(field.key()))
                .filter(value -> value != null)
                .collect(Collectors.joining(NAME_SEPARATOR));
    }

    private static String normalize(CategoryField field, String value) {
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new BusinessRuleException("Field '" + field.label() + "' is too long");
        }
        return switch (field.type()) {
            case TEXT -> value;
            case NUMBER -> {
                try {
                    yield new BigDecimal(value).toPlainString();
                } catch (NumberFormatException e) {
                    throw new BusinessRuleException("Field '" + field.label() + "' must be a number");
                }
            }
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new BusinessRuleException("Field '" + field.label() + "' must be true or false");
                }
                yield value.toLowerCase();
            }
            case DATE -> {
                try {
                    yield LocalDate.parse(value).toString();
                } catch (DateTimeParseException e) {
                    throw new BusinessRuleException("Field '" + field.label() + "' must be a date (YYYY-MM-DD)");
                }
            }
            case CHOICE -> {
                if (!field.choices().contains(value)) {
                    throw new BusinessRuleException("Field '" + field.label() + "' must be one of: "
                            + String.join(", ", field.choices()));
                }
                yield value;
            }
        };
    }
}
