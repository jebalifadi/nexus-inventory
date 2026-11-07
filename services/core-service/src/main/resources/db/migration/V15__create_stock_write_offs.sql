-- Goods that left stock without being sold: damaged, lost, stolen. Their real cost becomes a loss,
-- which lowers profit (not cash: the money already left when they were bought).
CREATE SEQUENCE write_off_public_id_seq;

CREATE TABLE stock_write_offs (
    id             BIGSERIAL    PRIMARY KEY,
    public_id      VARCHAR(20)  NOT NULL UNIQUE
                   DEFAULT ('WO-' || lpad(nextval('write_off_public_id_seq')::text, 6, '0')),
    unit_id        BIGINT       REFERENCES inventory_units (id),
    lot_id         BIGINT       REFERENCES inventory_lots (id),
    product_id     BIGINT       NOT NULL REFERENCES products (id),
    quantity       INT          NOT NULL CHECK (quantity > 0),
    reason         VARCHAR(255) NOT NULL,
    write_off_date DATE         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Set when the goods are restored from the trash: the loss no longer counts, the row stays as history.
    cancelled_at   TIMESTAMPTZ,
    CHECK ((unit_id IS NULL) <> (lot_id IS NULL))
);

-- The real cost lost, one row per currency (purchase in EUR and shipping in TND stay apart).
CREATE TABLE stock_write_off_costs (
    write_off_id BIGINT        NOT NULL REFERENCES stock_write_offs (id),
    currency     CHAR(3)       NOT NULL REFERENCES currencies (code),
    amount       NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (write_off_id, currency)
);

CREATE INDEX idx_stock_write_offs_date ON stock_write_offs (write_off_date);
CREATE INDEX idx_stock_write_offs_unit ON stock_write_offs (unit_id);
CREATE INDEX idx_stock_write_offs_lot ON stock_write_offs (lot_id);
