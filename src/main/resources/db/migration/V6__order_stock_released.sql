-- H-5: stock reserved at checkout must go back exactly once when an unpaid order dies
-- (admin cancel, Stripe intent canceled, unpaid-order expiry, or delete).
-- The flag is claimed with an atomic UPDATE ... WHERE stock_released = false, so two
-- paths racing (e.g. the expiry job and the payment_intent.canceled webhook) can't both restock.
ALTER TABLE orders
    ADD COLUMN stock_released BOOLEAN NOT NULL DEFAULT FALSE;

-- Feeds the unpaid-order expiry sweep and the per-user open-order cap.
CREATE INDEX IF NOT EXISTS idx_orders_unpaid_open
    ON orders (status, payment_status, created_at)
    WHERE stock_released = FALSE;
