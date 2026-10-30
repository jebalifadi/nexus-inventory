-- Buying: suppliers, purchases with lines, and the link from stock back to the line that bought it.

CREATE TABLE suppliers (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    country    VARCHAR(100),
    contact    VARCHAR(255),
    notes      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_suppliers_name ON suppliers (lower(name));

CREATE SEQUENCE purchase_public_id_seq;

CREATE TABLE purchases (
    id            BIGSERIAL   PRIMARY KEY,
    public_id     VARCHAR(20) NOT NULL UNIQUE
                  DEFAULT ('P-' || lpad(nextval('purchase_public_id_seq')::text, 6, '0')),
    supplier_id   BIGINT      NOT NULL REFERENCES suppliers (id),
    purchase_date DATE        NOT NULL,
    currency      CHAR(3)     NOT NULL REFERENCES currencies (code),
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_purchases_supplier_id ON purchases (supplier_id);

CREATE TABLE purchase_lines (
    id                BIGSERIAL     PRIMARY KEY,
    purchase_id       BIGINT        NOT NULL REFERENCES purchases (id),
    line_no           INT           NOT NULL,
    product_id        BIGINT        NOT NULL REFERENCES products (id),
    location_id       BIGINT        NOT NULL REFERENCES locations (id),
    quantity          INT           NOT NULL CHECK (quantity > 0),
    unit_price_amount NUMERIC(19,4) NOT NULL CHECK (unit_price_amount >= 0),
    UNIQUE (purchase_id, line_no)
);

CREATE INDEX idx_purchase_lines_product_id ON purchase_lines (product_id);

-- Every unit and lot remembers which purchase line brought it in (empty for stock entered by hand).
ALTER TABLE inventory_units ADD COLUMN purchase_line_id BIGINT REFERENCES purchase_lines (id);
ALTER TABLE inventory_lots  ADD COLUMN purchase_line_id BIGINT REFERENCES purchase_lines (id);

CREATE INDEX idx_units_purchase_line_id ON inventory_units (purchase_line_id);
CREATE INDEX idx_lots_purchase_line_id ON inventory_lots (purchase_line_id);
