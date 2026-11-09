-- One row: the owner's choices for the whole business. No row yet means the defaults.
CREATE TABLE business_settings (
    id                    SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    currency              CHAR(3)     REFERENCES currencies (code),
    low_stock_limit       INTEGER     NOT NULL CHECK (low_stock_limit >= 0),
    payment_reminder_days INTEGER     NOT NULL CHECK (payment_reminder_days >= 1),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
