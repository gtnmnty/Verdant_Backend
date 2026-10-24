-- New audit action types GIFT_CARD_APPLIED and PROMO_CODE_APPLIED (audit L-19).
-- A table created by Hibernate carries a CHECK constraint listing every AuditActionType value,
-- which would reject the new ones. Replace it with the current list (a no-op drop if it never existed).
-- NOT VALID: enforce it for new rows only, so the migration can't fail on rows written earlier.
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_action_type_check;
ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_action_type_check CHECK (action_type IN (
        'CREATED', 'BOOKED', 'RESTOCKED', 'FAVORITE', 'UPDATED', 'CANCELLED', 'DELETED',
        'SECURITY', 'ADMINISTRATIVE', 'STOCK_ADJUSTED', 'PAYMENT_INITIATED', 'PAYMENT_SUCCEEDED',
        'PAYMENT_FAILED', 'PAYMENT_REFUNDED', 'UNFAVORITED', 'RESCHEDULED', 'APPROVED', 'REJECTED',
        'COMPLETED', 'REVIEWED', 'IMAGE_UPDATED', 'STATUS_CHANGED', 'ASSIGNED', 'BULK_CANCELLED',
        'BULK_DELETED', 'GIFT_CARD', 'ISSUED', 'REDEEMED', 'EXPIRED', 'GIFT_CARD_APPLIED',
        'PROMO_CODE_APPLIED'
    )) NOT VALID;
