-- A cancelled order gives the customer's promo use back. The redemption row stays (audit trail, and so staff
-- re-opening the order can take the use again); released_at marks it as no longer counting toward max_uses_per_user.
ALTER TABLE promo_code_redemptions
    ADD COLUMN released_at TIMESTAMPTZ;

-- Orders cancelled before this migration already consumed a use; give those back too.
UPDATE promo_code_redemptions r
SET released_at = COALESCE(o.cancelled_at, now())
FROM orders o
WHERE r.order_id = o.id
  AND o.order_status = 'CANCELLED'
  AND r.released_at IS NULL;
