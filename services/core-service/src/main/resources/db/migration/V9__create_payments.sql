-- Payments: money received from customers for sales, and money paid to suppliers for purchases.
-- Each payment is its own record, so a sale can be paid in several parts.

CREATE SEQUENCE payment_public_id_seq;

CREATE TABLE payments (
    id           BIGSERIAL     PRIMARY KEY,
    public_id    VARCHAR(20)   NOT NULL UNIQUE
                 DEFAULT ('PAY-' || lpad(nextval('payment_public_id_seq')::text, 6, '0')),
    direction    VARCHAR(10)   NOT NULL CHECK (direction IN ('INCOMING', 'OUTGOING')),
    sale_id      BIGINT        REFERENCES sales (id),
    purchase_id  BIGINT        REFERENCES purchases (id),
    amount       NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency     CHAR(3)       NOT NULL REFERENCES currencies (code),
    payment_date DATE          NOT NULL,
    method       VARCHAR(20)   NOT NULL
                 CHECK (method IN ('CASH', 'BANK_TRANSFER', 'CARD', 'PAYPAL', 'OTHER')),
    notes        TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- A payment belongs to exactly one sale (money in) or one purchase (money out).
    CHECK ((sale_id IS NULL) <> (purchase_id IS NULL)),
    CHECK ((direction = 'INCOMING') = (sale_id IS NOT NULL))
);

CREATE INDEX idx_payments_sale_id ON payments (sale_id);
CREATE INDEX idx_payments_purchase_id ON payments (purchase_id);
CREATE INDEX idx_payments_payment_date ON payments (payment_date);
