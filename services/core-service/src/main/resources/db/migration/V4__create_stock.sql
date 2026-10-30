-- Stock: warehouses, individually tracked units, and lots of identical goods.

CREATE TABLE locations (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    country    VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE SEQUENCE unit_public_id_seq;
CREATE SEQUENCE lot_public_id_seq;

CREATE TABLE inventory_units (
    id                 BIGSERIAL     PRIMARY KEY,
    public_id          VARCHAR(20)   NOT NULL UNIQUE
                       DEFAULT ('U-' || lpad(nextval('unit_public_id_seq')::text, 6, '0')),
    product_id         BIGINT        NOT NULL REFERENCES products (id),
    location_id        BIGINT        NOT NULL REFERENCES locations (id),
    status             VARCHAR(20)   NOT NULL CHECK (status IN ('PURCHASED', 'IN_TRANSIT', 'IN_INVENTORY',
                       'LISTED', 'RESERVED', 'SOLD', 'CLOSED', 'RETURNED', 'CANCELLED')),
    unit_cost_amount   NUMERIC(19,4) NOT NULL CHECK (unit_cost_amount >= 0),
    unit_cost_currency CHAR(3)       NOT NULL REFERENCES currencies (code),
    received_date      DATE          NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_units_product_id ON inventory_units (product_id);
CREATE INDEX idx_units_location_id ON inventory_units (location_id);
CREATE INDEX idx_units_status ON inventory_units (status);

CREATE TABLE unit_attributes (
    unit_id  BIGINT       NOT NULL REFERENCES inventory_units (id),
    field_id BIGINT       NOT NULL REFERENCES category_fields (id),
    value    VARCHAR(255) NOT NULL,
    PRIMARY KEY (unit_id, field_id)
);

CREATE TABLE unit_status_history (
    id          BIGSERIAL   PRIMARY KEY,
    unit_id     BIGINT      NOT NULL REFERENCES inventory_units (id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory_lots (
    id                 BIGSERIAL     PRIMARY KEY,
    public_id          VARCHAR(20)   NOT NULL UNIQUE
                       DEFAULT ('LOT-' || lpad(nextval('lot_public_id_seq')::text, 6, '0')),
    product_id         BIGINT        NOT NULL REFERENCES products (id),
    location_id        BIGINT        NOT NULL REFERENCES locations (id),
    quantity_received  INT           NOT NULL CHECK (quantity_received > 0),
    quantity_remaining INT           NOT NULL CHECK (quantity_remaining >= 0),
    unit_cost_amount   NUMERIC(19,4) NOT NULL CHECK (unit_cost_amount >= 0),
    unit_cost_currency CHAR(3)       NOT NULL REFERENCES currencies (code),
    received_date      DATE          NOT NULL,
    status             VARCHAR(20)   NOT NULL CHECK (status IN ('IN_TRANSIT', 'IN_STOCK', 'DEPLETED')),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (quantity_remaining <= quantity_received)
);

CREATE INDEX idx_lots_product_id ON inventory_lots (product_id);
CREATE INDEX idx_lots_location_id ON inventory_lots (location_id);
