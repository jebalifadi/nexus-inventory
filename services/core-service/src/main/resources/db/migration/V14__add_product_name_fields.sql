-- The owner chooses which product fields form a product's name, and in which order.
-- NULL means the field is not part of the name; it is shown as its own column instead.
ALTER TABLE category_fields ADD COLUMN name_position INT;
ALTER TABLE category_fields ADD CONSTRAINT chk_name_position_product
    CHECK (name_position IS NULL OR scope = 'PRODUCT');

-- Existing categories keep every product field in the name, in field order.
UPDATE category_fields SET name_position = position WHERE scope = 'PRODUCT';

-- Names are now joined with " · " instead of ", ". Falls back to the SKU, like the application.
UPDATE products p
SET name = COALESCE(
        (SELECT string_agg(pa.value, ' · ' ORDER BY cf.name_position)
         FROM product_attributes pa
         JOIN category_fields cf ON cf.id = pa.field_id
         WHERE pa.product_id = p.id AND cf.name_position IS NOT NULL),
        p.sku);
