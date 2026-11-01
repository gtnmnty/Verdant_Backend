-- Order overhaul, phase 2: an order row is written only after payment succeeds.
-- Until then the checkout lives here as a draft. It holds no stock and never shows up in order history.
-- Deleted when the order is created, or purged after expires_at (UnpaidOrderExpiryJob).
CREATE TABLE pending_checkouts (
    id                       UUID PRIMARY KEY,
    user_id                  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_by_user_id       UUID REFERENCES users (id) ON DELETE SET NULL, -- staff, for orders placed on a customer's behalf
    items                    JSONB NOT NULL,                                 -- [{productId, quantity, deliveryOption, unitPrice}]
    address_line1            VARCHAR(255),
    address_line2            VARCHAR(255),
    address_city             VARCHAR(100),
    address_state            VARCHAR(100),
    address_postal           VARCHAR(20),
    address_country          VARCHAR(5),
    contact_name             VARCHAR(100) NOT NULL,
    contact_phone            VARCHAR(20)  NOT NULL,
    contact_email            VARCHAR(255) NOT NULL,
    payment_method           VARCHAR(50)  NOT NULL,
    promo_code               VARCHAR(50),
    promo_discount_amount    NUMERIC(10, 2) NOT NULL DEFAULT 0,
    subtotal                 NUMERIC(10, 2) NOT NULL,
    delivery_fee             NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total                    NUMERIC(10, 2) NOT NULL,
    wallet_amount_applied    NUMERIC(10, 2) NOT NULL DEFAULT 0,
    amount_due               NUMERIC(10, 2) NOT NULL,                        -- what Stripe charges: total - wallet
    currency                 VARCHAR(10)  NOT NULL,
    stripe_payment_intent_id VARCHAR(255),
    expires_at               TIMESTAMPTZ  NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_pending_checkouts_intent
    ON pending_checkouts (stripe_payment_intent_id) WHERE stripe_payment_intent_id IS NOT NULL;
CREATE INDEX idx_pending_checkouts_expires ON pending_checkouts (expires_at);
CREATE INDEX idx_pending_checkouts_user ON pending_checkouts (user_id);
