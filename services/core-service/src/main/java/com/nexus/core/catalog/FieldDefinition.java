package com.nexus.core.catalog;

import java.util.List;

/** A field as the owner describes it when creating a category. */
public record FieldDefinition(
        String label, FieldType type, FieldScope scope, boolean required, List<String> choices) {
}
