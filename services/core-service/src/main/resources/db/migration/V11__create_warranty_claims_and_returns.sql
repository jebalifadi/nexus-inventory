-- Warranty claims, returns and refunds.

-- Refunds are payment records too (never deletions). Their amount stays positive; the kind gives the sign.
ALTER TABLE payments ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'PAYMENT'
    CHECK (kind IN ('PAYMENT', 'REFUND'));

-- How many pieces of a line came back. A fully returned unit line frees the unit to be sold again.
ALTER TABLE sale_lines ADD COLUMN returned_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE sale_lines ADD CONSTRAINT chk_sale_lines_returned_quantity
    CHECK (returned_quantity BETWEEN 0 AND quantity);

-- Returned lot pieces go back into the same lots they were taken from.
ALTER TABLE stock_allocations ADD COLUMN returned_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE stock_allocations ADD CONSTRAINT chk_stock_allocations_returned_quantity
    CHECK (returned_quantity BETWEEN 0 AND quantity);

CREATE SEQUENCE warranty_claim_public_id_seq;

CREATE TABLE warranty_claims (
    id              BIGSERIAL     PRIMARY KEY,
    public_id       VARCHAR(20)   NOT NULL UNIQUE
                    DEFAULT ('WC-' || lpad(nextval('warranty_claim_public_id_seq')::text, 6, '0')),
    sale_line_id    BIGINT        NOT NULL REFERENCES sale_lines (id),
    quantity        INT           NOT NULL CHECK (quantity > 0),
    claim_date      DATE          NOT NULL,
    issue           VARCHAR(1000) NOT NULL,
    within_warranty BOOLEAN       NOT NULL,
    goodwill_reason VARCHAR(500),
    status          VARCHAR(20)   NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'REJECTED')),
    resolution      VARCHAR(20)   CHECK (resolution IN ('REPAIRED', 'REPLACED', 'REFUNDED', 'REJECTED')),
    cost_amount     NUMERIC(19,4) CHECK (cost_amount >= 0),
    cost_currency   CHAR(3)       REFERENCES currencies (code),
    refund_amount   NUMERIC(19,4) CHECK (refund_amount >= 0),
    resolved_date   DATE,
    notes           TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- A claim after the warranty ended is only accepted as goodwill, with a reason.
    CHECK (within_warranty OR goodwill_reason IS NOT NULL),
    CHECK ((status = 'OPEN') = (resolution IS NULL))
);

CREATE INDEX idx_warranty_claims_sale_line_id ON warranty_claims (sale_line_id);
CREATE INDEX idx_warranty_claims_status ON warranty_claims (status);
