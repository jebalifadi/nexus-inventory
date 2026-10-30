package com.nexus.core.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes catalog data with plain SQL. */
@Repository
class CatalogRepository {

    private final JdbcClient jdbc;

    CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long insertCategory(String name, TrackingMode trackingMode) {
        return jdbc.sql("INSERT INTO categories (name, tracking_mode) VALUES (:name, :mode) RETURNING id")
                .param("name", name)
                .param("mode", trackingMode.name())
                .query(Long.class)
                .single();
    }

    long insertField(long categoryId, CategoryField field, int position) {
        return jdbc.sql("""
                        INSERT INTO category_fields
                            (category_id, field_key, label, field_type, scope, required, position)
                        VALUES (:categoryId, :key, :label, :type, :scope, :required, :position)
                        RETURNING id""")
                .param("categoryId", categoryId)
                .param("key", field.key())
                .param("label", field.label())
                .param("type", field.type().name())
                .param("scope", field.scope().name())
                .param("required", field.required())
                .param("position", position)
                .query(Long.class)
                .single();
    }

    void insertChoice(long fieldId, String value, int position) {
        jdbc.sql("INSERT INTO category_field_choices (field_id, position, value) VALUES (:fieldId, :position, :value)")
                .param("fieldId", fieldId)
                .param("position", position)
                .param("value", value)
                .update();
    }

    Optional<Category> findCategory(long id) {
        record Row(long id, String name, TrackingMode mode) {
        }
        Optional<Row> row = jdbc.sql("SELECT id, name, tracking_mode FROM categories WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("name"),
                        TrackingMode.valueOf(rs.getString("tracking_mode"))))
                .optional();
        return row.map(r -> new Category(r.id(), r.name(), r.mode(), findFields(r.id())));
    }

    List<Long> findAllCategoryIds() {
        return jdbc.sql("SELECT id FROM categories ORDER BY name").query(Long.class).list();
    }

    long insertProduct(long categoryId, String sku, String name) {
        return jdbc.sql("""
                        INSERT INTO products (category_id, sku, name)
                        VALUES (:categoryId, :sku, :name)
                        RETURNING id""")
                .param("categoryId", categoryId)
                .param("sku", sku)
                .param("name", name)
                .query(Long.class)
                .single();
    }

    void insertAttribute(long productId, long fieldId, String value) {
        jdbc.sql("INSERT INTO product_attributes (product_id, field_id, value) VALUES (:productId, :fieldId, :value)")
                .param("productId", productId)
                .param("fieldId", fieldId)
                .param("value", value)
                .update();
    }

    Optional<Product> findProduct(long id) {
        record Row(long id, long categoryId, String sku, String name) {
        }
        Optional<Row> row = jdbc.sql("SELECT id, category_id, sku, name FROM products WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getLong("category_id"),
                        rs.getString("sku"), rs.getString("name")))
                .optional();
        return row.map(r -> new Product(r.id(), r.categoryId(), r.sku(), r.name(), findAttributes(r.id())));
    }

    List<Long> findProductIds() {
        return jdbc.sql("SELECT id FROM products ORDER BY id").query(Long.class).list();
    }

    List<Long> findProductIdsByCategory(long categoryId) {
        return jdbc.sql("SELECT id FROM products WHERE category_id = :categoryId ORDER BY id")
                .param("categoryId", categoryId)
                .query(Long.class)
                .list();
    }

    private List<CategoryField> findFields(long categoryId) {
        Map<Long, List<String>> choices = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT c.field_id, c.value
                        FROM category_field_choices c
                        JOIN category_fields f ON f.id = c.field_id
                        WHERE f.category_id = :categoryId
                        ORDER BY c.field_id, c.position""")
                .param("categoryId", categoryId)
                .query((rs, n) -> Map.entry(rs.getLong("field_id"), rs.getString("value")))
                .list()
                .forEach(e -> choices.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));

        return jdbc.sql("""
                        SELECT id, field_key, label, field_type, scope, required
                        FROM category_fields
                        WHERE category_id = :categoryId
                        ORDER BY position""")
                .param("categoryId", categoryId)
                .query((rs, n) -> new CategoryField(
                        rs.getLong("id"),
                        rs.getString("field_key"),
                        rs.getString("label"),
                        FieldType.valueOf(rs.getString("field_type")),
                        FieldScope.valueOf(rs.getString("scope")),
                        rs.getBoolean("required"),
                        choices.getOrDefault(rs.getLong("id"), List.of())))
                .list();
    }

    private Map<String, String> findAttributes(long productId) {
        Map<String, String> attributes = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT f.field_key, a.value
                        FROM product_attributes a
                        JOIN category_fields f ON f.id = a.field_id
                        WHERE a.product_id = :productId
                        ORDER BY f.position""")
                .param("productId", productId)
                .query((rs, n) -> Map.entry(rs.getString("field_key"), rs.getString("value")))
                .list()
                .forEach(e -> attributes.put(e.getKey(), e.getValue()));
        return attributes;
    }
}
