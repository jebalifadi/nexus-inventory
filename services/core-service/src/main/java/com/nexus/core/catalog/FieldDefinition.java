package com.nexus.core.catalog;

import java.util.List;

/** A field as the owner describes it when creating a category. */
public record FieldDefinition(
        String label, FieldType type, FieldScope scope, boolean required, List<String> choices,
        /** Part of the product name? Name fields keep the order they are listed in. */
        boolean inName) {

    /** A field that is not marked for the name. */
    public FieldDefinition(String label, FieldType type, FieldScope scope, boolean required, List<String> choices) {
        this(label, type, scope, required, choices, false);
    }
}
