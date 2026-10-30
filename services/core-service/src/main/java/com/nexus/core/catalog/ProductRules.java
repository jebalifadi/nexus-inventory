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

    /** Returns the validated values, in the category's field order. */
    static Map<String, String> validateAttributes(Category category, Map<String, String> input) {
        Map<String, String> values = input == null ? Map.of() : input;
        for (String key : values.keySet()) {
            CategoryField field = category.field(key).orElseThrow(() -> new BusinessRuleException(
                    "Unknown field '" + key + "' for category '" + category.name() + "'"));
            if (field.scope() == FieldScope.ITEM) {
                throw new BusinessRuleException(
                        "Field '" + field.label() + "' is filled in for each item, not for the product");
            }
        }
        Map<String, String> validated = new LinkedHashMap<>();
        for (CategoryField field : category.productFields()) {
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

    /** Builds a readable product name from the product fields, for example "iPhone 14 Pro, 128 GB". */
    static String buildName(Category category, Map<String, String> attributes) {
        return category.productFields().stream()
                .map(field -> attributes.get(field.key()))
                .filter(value -> value != null)
                .collect(Collectors.joining(", "));
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
