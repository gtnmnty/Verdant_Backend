-- GiftCardStatus.DEPLETED (a card spent down to zero) was never allowed by the CHECK from V1, so the checkout
-- that used up a card's last dollar failed with "This change conflicts with existing data".
ALTER TABLE gift_cards DROP CONSTRAINT IF EXISTS gift_cards_status_check;
ALTER TABLE gift_cards
    ADD CONSTRAINT gift_cards_status_check CHECK (status IN (
        'ACTIVE', 'REDEEMED', 'DEPLETED', 'EXPIRED', 'CANCELLED'
    ));
