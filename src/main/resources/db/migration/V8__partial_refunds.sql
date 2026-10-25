-- M-14: partial Stripe refunds.
-- 1) New payment status PARTIALLY_REFUNDED (orders and gift cards). Tables created by Hibernate carry a
--    CHECK constraint listing every PaymentStatus value, which would reject the new one. Replace it with
--    the current list (a no-op drop if it never existed). NOT VALID: checked for new rows only, so the
--    migration can't fail on rows written earlier. Same approach as V5.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check;
ALTER TABLE orders
    ADD CONSTRAINT orders_payment_status_check CHECK (payment_status IN (
        'PENDING', 'REQUIRES_ACTION', 'PROCESSED', 'PAID', 'FAILED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'CANCELLED'
    )) NOT VALID;

ALTER TABLE gift_cards DROP CONSTRAINT IF EXISTS gift_cards_payment_status_check;
ALTER TABLE gift_cards
    ADD CONSTRAINT gift_cards_payment_status_check CHECK (payment_status IN (
        'PENDING', 'REQUIRES_ACTION', 'PROCESSED', 'PAID', 'FAILED', 'PARTIALLY_REFUNDED', 'REFUNDED', 'CANCELLED'
    )) NOT VALID;

-- 2) How much of a gift card's purchase Stripe has refunded so far, already taken off its balance.
--    charge.refunded reports a running total; storing what was applied makes a repeated or out-of-order
--    webhook take off only the difference, never the same refund twice.
ALTER TABLE gift_cards
    ADD COLUMN refunded_amount NUMERIC(10, 2) NOT NULL DEFAULT 0;
