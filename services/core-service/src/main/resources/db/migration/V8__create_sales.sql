-- Selling: customers, sales with lines, and which lots each quantity line was taken from (FIFO).

CREATE TABLE customers (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    phone      VARCHAR(50),
    email      VARCHAR(150),
    country    VARCHAR(100),
    notes      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE SEQUENCE sale_public_id_seq;

CREATE TABLE sales (
    id          BIGSERIAL   PRIMARY KEY,
    public_id   VARCHAR(20) NOT NULL UNIQUE
                DEFAULT ('S-' || lpad(nextval('sale_public_id_seq')::text, 6, '0')),
    customer_id BIGINT      REFERENCES customers (id),
    location_id BIGINT      NOT NULL REFERENCES locations (id),
    sale_date   DATE        NOT NULL,
    currency    CHAR(3)     NOT NULL REFERENCES currencies (code),
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sales_customer_id ON sales (customer_id);
CREATE INDEX idx_sales_sale_date ON sales (sale_date);

-- cost_* is frozen when the line is saved: the real (landed) cost of what was sold.
-- It stays empty when purchase and shipping were paid in different currencies, until exchange rates exist.
CREATE TABLE sale_lines (
    id                BIGSERIAL     PRIMARY KEY,
    sale_id           BIGINT        NOT NULL REFERENCES sales (id),
    line_no           INT           NOT NULL,
    product_id        BIGINT        NOT NULL REFERENCES products (id),
    unit_id           BIGINT        REFERENCES inventory_units (id),
    quantity          INT           NOT NULL CHECK (quantity > 0),
    unit_price_amount NUMERIC(19,4) NOT NULL CHECK (unit_price_amount >= 0),
    cost_amount       NUMERIC(19,4) CHECK (cost_amount >= 0),
    cost_currency     CHAR(3)       REFERENCES currencies (code),
    warranty_days     INT           NOT NULL DEFAULT 0 CHECK (warranty_days BETWEEN 0 AND 3650),
    warranty_terms    VARCHAR(500),
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETURNED')),
    UNIQUE (sale_id, line_no),
    CHECK (unit_id IS NULL OR quantity = 1),
    CHECK ((cost_amount IS NULL) = (cost_currency IS NULL))
);

CREATE INDEX idx_sale_lines_product_id ON sale_lines (product_id);

-- A unit can be on only one active sale line at a time.
CREATE UNIQUE INDEX uq_sale_lines_active_unit ON sale_lines (unit_id)
    WHERE unit_id IS NOT NULL AND status = 'ACTIVE';

CREATE TABLE stock_allocations (
    id                 BIGSERIAL     PRIMARY KEY,
    sale_line_id       BIGINT        NOT NULL REFERENCES sale_lines (id),
    lot_id             BIGINT        NOT NULL REFERENCES inventory_lots (id),
    quantity           INT           NOT NULL CHECK (quantity > 0),
    unit_cost_amount   NUMERIC(19,4) CHECK (unit_cost_amount >= 0),
    unit_cost_currency CHAR(3)       REFERENCES currencies (code),
    CHECK ((unit_cost_amount IS NULL) = (unit_cost_currency IS NULL))
);

CREATE INDEX idx_stock_allocations_sale_line_id ON stock_allocations (sale_line_id);
CREATE INDEX idx_stock_allocations_lot_id ON stock_allocations (lot_id);
