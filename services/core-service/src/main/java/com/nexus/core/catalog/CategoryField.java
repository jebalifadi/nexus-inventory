package com.nexus.core.catalog;

import java.util.List;

/** A field of a category, with its stable internal key (for example {@code speicher}). */
public record CategoryField(
        Long id,
        String key,
        String label,
        FieldType type,
        FieldScope scope,
        boolean required,
        List<String> choices) {

    public CategoryField {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }
}
