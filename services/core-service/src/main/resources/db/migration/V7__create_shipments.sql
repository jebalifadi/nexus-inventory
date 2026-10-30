-- Shipping: goods on their way between two locations, and the shipping cost each item carries.

CREATE SEQUENCE shipment_public_id_seq;

CREATE TABLE shipments (
    id                BIGSERIAL     PRIMARY KEY,
    public_id         VARCHAR(20)   NOT NULL UNIQUE
                      DEFAULT ('SH-' || lpad(nextval('shipment_public_id_seq')::text, 6, '0')),
    from_location_id  BIGINT        NOT NULL REFERENCES locations (id),
    to_location_id    BIGINT        NOT NULL REFERENCES locations (id),
    carrier           VARCHAR(100),
    tracking_number   VARCHAR(100),
    cost_amount       NUMERIC(19,4) NOT NULL CHECK (cost_amount >= 0),
    cost_currency     CHAR(3)       NOT NULL REFERENCES currencies (code),
    allocation_method VARCHAR(20)   NOT NULL CHECK (allocation_method IN ('BY_VALUE', 'BY_QUANTITY')),
    status            VARCHAR(20)   NOT NULL CHECK (status IN ('IN_TRANSIT', 'ARRIVED')),
    shipped_date      DATE          NOT NULL,
    arrived_date      DATE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (from_location_id <> to_location_id),
    CHECK (arrived_date IS NULL OR arrived_date >= shipped_date)
);

CREATE INDEX idx_shipments_status ON shipments (status);

-- What is inside: one row per unit or per lot, with its share of the shipping cost.
CREATE TABLE shipment_contents (
    id                    BIGSERIAL     PRIMARY KEY,
    shipment_id           BIGINT        NOT NULL REFERENCES shipments (id),
    unit_id               BIGINT        REFERENCES inventory_units (id),
    lot_id                BIGINT        REFERENCES inventory_lots (id),
    quantity              INT           NOT NULL CHECK (quantity > 0),
    allocated_cost_amount NUMERIC(19,4) NOT NULL CHECK (allocated_cost_amount >= 0),
    CHECK ((unit_id IS NULL) <> (lot_id IS NULL))
);

CREATE INDEX idx_shipment_contents_shipment_id ON shipment_contents (shipment_id);
CREATE INDEX idx_shipment_contents_unit_id ON shipment_contents (unit_id);
CREATE INDEX idx_shipment_contents_lot_id ON shipment_contents (lot_id);
