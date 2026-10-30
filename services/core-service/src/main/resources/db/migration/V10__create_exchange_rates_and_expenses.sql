-- Exchange rates by day, and extra costs (repairs, fees, customs...).

CREATE TABLE exchange_rates (
    id         BIGSERIAL     PRIMARY KEY,
    base       CHAR(3)       NOT NULL REFERENCES currencies (code),
    quote      CHAR(3)       NOT NULL REFERENCES currencies (code),
    rate       NUMERIC(19,8) NOT NULL CHECK (rate > 0),
    rate_date  DATE          NOT NULL,
    source     VARCHAR(50)   NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (base <> quote),
    UNIQUE (base, quote, rate_date)
);

CREATE SEQUENCE expense_public_id_seq;

-- An expense belongs to one unit (a repair), one sale (platform fees), or to nothing (general costs).
CREATE TABLE expenses (
    id           BIGSERIAL     PRIMARY KEY,
    public_id    VARCHAR(20)   NOT NULL UNIQUE
                 DEFAULT ('EXP-' || lpad(nextval('expense_public_id_seq')::text, 6, '0')),
    category     VARCHAR(20)   NOT NULL CHECK (category IN ('SHIPPING', 'REPAIR', 'TRANSPORT', 'CUSTOMS',
                 'PAYMENT_FEES', 'PLATFORM_FEES', 'PACKAGING', 'PARTS', 'WARRANTY', 'OTHER')),
    amount       NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency     CHAR(3)       NOT NULL REFERENCES currencies (code),
    expense_date DATE          NOT NULL,
    description  VARCHAR(500),
    unit_id      BIGINT        REFERENCES inventory_units (id),
    sale_id      BIGINT        REFERENCES sales (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (unit_id IS NULL OR sale_id IS NULL)
);

CREATE INDEX idx_expenses_unit_id ON expenses (unit_id);
CREATE INDEX idx_expenses_sale_id ON expenses (sale_id);
CREATE INDEX idx_expenses_expense_date ON expenses (expense_date);
