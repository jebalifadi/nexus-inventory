package com.nexus.core.catalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A sellable product (SKU) of a category, with its product-level attribute values. */
public record Product(Long id, Long categoryId, String sku, String name, Map<String, String> attributes) {

    public Product {
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
