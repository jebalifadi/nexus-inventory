package com.nexus.core.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** An owner-defined type of goods, for example "Phones", with its own fields. */
public record Category(Long id, String name, TrackingMode trackingMode, List<CategoryField> fields) {

    public Category {
        fields = List.copyOf(fields);
    }

    public List<CategoryField> productFields() {
        return fields(FieldScope.PRODUCT);
    }

    /** The fields that form a product's name, in name order. */
    public List<CategoryField> nameFields() {
        return fields.stream()
                .filter(field -> field.namePosition() != null)
                .sorted(Comparator.comparing(CategoryField::namePosition))
                .toList();
    }

    public List<CategoryField> fields(FieldScope scope) {
        return fields.stream().filter(field -> field.scope() == scope).toList();
    }

    public Optional<CategoryField> field(String key) {
        return fields.stream().filter(field -> field.key().equals(key)).findFirst();
    }
}
