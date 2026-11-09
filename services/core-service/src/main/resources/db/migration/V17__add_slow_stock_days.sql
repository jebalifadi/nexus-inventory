ALTER TABLE business_settings ADD COLUMN slow_stock_days INTEGER NOT NULL DEFAULT 90 CHECK (slow_stock_days >= 1);
