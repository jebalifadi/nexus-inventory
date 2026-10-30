-- Catalog: owner-defined categories, their fields, and products.

CREATE TABLE categories (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    tracking_mode VARCHAR(10)  NOT NULL CHECK (tracking_mode IN ('UNIT', 'LOT')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE category_fields (
    id          BIGSERIAL    PRIMARY KEY,
    category_id BIGINT       NOT NULL REFERENCES categories (id),
    field_key   VARCHAR(64)  NOT NULL,
    label       VARCHAR(100) NOT NULL,
    field_type  VARCHAR(10)  NOT NULL CHECK (field_type IN ('TEXT', 'NUMBER', 'CHOICE', 'BOOLEAN', 'DATE')),
    scope       VARCHAR(10)  NOT NULL CHECK (scope IN ('PRODUCT', 'ITEM')),
    required    BOOLEAN      NOT NULL DEFAULT FALSE,
    position    INT          NOT NULL,
    UNIQUE (category_id, field_key)
);

CREATE TABLE category_field_choices (
    field_id BIGINT       NOT NULL REFERENCES category_fields (id),
    position INT          NOT NULL,
    value    VARCHAR(100) NOT NULL,
    PRIMARY KEY (field_id, value)
);

CREATE TABLE products (
    id          BIGSERIAL    PRIMARY KEY,
    category_id BIGINT       NOT NULL REFERENCES categories (id),
    sku         VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category_id ON products (category_id);

CREATE TABLE product_attributes (
    product_id BIGINT       NOT NULL REFERENCES products (id),
    field_id   BIGINT       NOT NULL REFERENCES category_fields (id),
    value      VARCHAR(255) NOT NULL,
    PRIMARY KEY (product_id, field_id)
);
