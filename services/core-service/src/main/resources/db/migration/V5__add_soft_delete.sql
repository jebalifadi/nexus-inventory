-- Soft delete: a deleted record disappears from lists but stays in history and reports.

ALTER TABLE categories      ADD COLUMN deleted_at TIMESTAMPTZ, ADD COLUMN delete_reason VARCHAR(255);
ALTER TABLE products        ADD COLUMN deleted_at TIMESTAMPTZ, ADD COLUMN delete_reason VARCHAR(255);
ALTER TABLE locations       ADD COLUMN deleted_at TIMESTAMPTZ, ADD COLUMN delete_reason VARCHAR(255);
ALTER TABLE inventory_units ADD COLUMN deleted_at TIMESTAMPTZ, ADD COLUMN delete_reason VARCHAR(255);
ALTER TABLE inventory_lots  ADD COLUMN deleted_at TIMESTAMPTZ, ADD COLUMN delete_reason VARCHAR(255);

-- Names and SKUs only need to be unique among records that are not deleted,
-- so a deleted "Berlin storage" does not block creating a new one.
ALTER TABLE categories DROP CONSTRAINT categories_name_key;
CREATE UNIQUE INDEX uq_categories_name_active ON categories (name) WHERE deleted_at IS NULL;

ALTER TABLE products DROP CONSTRAINT products_sku_key;
CREATE UNIQUE INDEX uq_products_sku_active ON products (sku) WHERE deleted_at IS NULL;

ALTER TABLE locations DROP CONSTRAINT locations_name_key;
CREATE UNIQUE INDEX uq_locations_name_active ON locations (name) WHERE deleted_at IS NULL;
