-- Cash and capital. Cash itself is never typed in: it is calculated from every movement
-- (payments, refunds, expenses, capital, currency exchanges), so it can never drift.

CREATE SEQUENCE capital_movement_public_id_seq;

CREATE TABLE capital_movements (
    id            BIGSERIAL     PRIMARY KEY,
    public_id     VARCHAR(20)   NOT NULL UNIQUE
                  DEFAULT ('CAP-' || lpad(nextval('capital_movement_public_id_seq')::text, 6, '0')),
    type          VARCHAR(20)   NOT NULL
                  CHECK (type IN ('OPENING_BALANCE', 'CAPITAL_ADDED', 'CAPITAL_WITHDRAWN', 'PROFIT_WITHDRAWN')),
    amount        NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency      CHAR(3)       NOT NULL REFERENCES currencies (code),
    movement_date DATE          NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE SEQUENCE currency_exchange_public_id_seq;

-- Changing money, for example 300 USD into 900 TND at an exchange office.
CREATE TABLE currency_exchanges (
    id            BIGSERIAL     PRIMARY KEY,
    public_id     VARCHAR(20)   NOT NULL UNIQUE
                  DEFAULT ('FX-' || lpad(nextval('currency_exchange_public_id_seq')::text, 6, '0')),
    from_amount   NUMERIC(19,4) NOT NULL CHECK (from_amount > 0),
    from_currency CHAR(3)       NOT NULL REFERENCES currencies (code),
    to_amount     NUMERIC(19,4) NOT NULL CHECK (to_amount > 0),
    to_currency   CHAR(3)       NOT NULL REFERENCES currencies (code),
    exchange_date DATE          NOT NULL,
    notes         TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (from_currency <> to_currency)
);
