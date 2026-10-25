-- M-15: optimistic locking. Each row gets a version counter that Hibernate checks and bumps on every
-- entity UPDATE (... SET version = version + 1 WHERE id = ? AND version = ?). When two writers loaded the
-- same row, the second one updates 0 rows and fails with a 409 instead of silently overwriting the first
-- (e.g. an admin product edit writing back a stale stock_quantity, or a webhook overwriting an admin edit).
-- DEFAULT 0 fills existing rows, so entities already in the database load with version 0.
ALTER TABLE products     ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE orders       ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE gift_cards   ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE promo_codes  ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
