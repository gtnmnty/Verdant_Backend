-- Order overhaul, phase 1: contact email, tracking number, paid date and customer-cancel details.
-- The pending_checkouts draft table (phase 2) is added in a later migration.
ALTER TABLE orders
    ADD COLUMN contact_email   VARCHAR(255),
    ADD COLUMN tracking_number VARCHAR(20),
    ADD COLUMN paid_at         TIMESTAMPTZ,
    ADD COLUMN cancel_reason   VARCHAR(200),
    ADD COLUMN cancelled_at    TIMESTAMPTZ,
    ADD COLUMN cancelled_by    VARCHAR(20);

-- Contact details default to the account's, so existing orders show the same name/phone/email as before.
UPDATE orders o
SET contact_name  = COALESCE(o.contact_name, u.full_name),
    contact_phone = COALESCE(o.contact_phone, u.phone),
    contact_email = COALESCE(o.contact_email, u.email)
FROM users u
WHERE o.user_id = u.id;

-- Approximation: the real payment time wasn't stored, so the order date stands in for it.
UPDATE orders
SET paid_at = created_at
WHERE payment_status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')
  AND paid_at IS NULL;

-- Tracking number: "VT-" + 8 characters from an alphabet without 0/O/1/I (same format as the app).
-- A retry loop covers the (very unlikely) collision; the unique index below is the final guard.
DO $$
DECLARE
    alphabet CONSTANT TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    r        RECORD;
    candidate TEXT;
BEGIN
    FOR r IN SELECT id FROM orders WHERE tracking_number IS NULL LOOP
        LOOP
            candidate := 'VT-';
            FOR i IN 1..8 LOOP
                candidate := candidate || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
            END LOOP;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM orders WHERE tracking_number = candidate);
        END LOOP;
        UPDATE orders SET tracking_number = candidate WHERE id = r.id;
    END LOOP;
END $$;

CREATE UNIQUE INDEX uq_orders_tracking_number ON orders (tracking_number);

-- One order per Stripe payment: makes order creation from a payment idempotent (phase 2 relies on it).
CREATE UNIQUE INDEX uq_orders_stripe_payment_intent_id
    ON orders (stripe_payment_intent_id) WHERE stripe_payment_intent_id IS NOT NULL;
