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
        List<String> choices,
        /** Place of this field in the product name, from 0; null when it is not part of the name. */
        Integer namePosition) {

    public CategoryField {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }
}
