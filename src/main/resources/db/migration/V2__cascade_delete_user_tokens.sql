-- CASCADE: personal / auth data
ALTER TABLE refresh_tokens
DROP CONSTRAINT refresh_tokens_user_id_fkey,
    ADD CONSTRAINT refresh_tokens_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE password_reset_tokens
DROP CONSTRAINT password_reset_tokens_user_id_fkey,
    ADD CONSTRAINT password_reset_tokens_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE favorites
DROP CONSTRAINT fk_favorites_user,
    ADD CONSTRAINT fk_favorites_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE cart_items
DROP CONSTRAINT fk_cart_user,
    ADD CONSTRAINT fk_cart_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE notifications
DROP CONSTRAINT fk_notifications_user,
    ADD CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- SET NULL: keep the record, drop the reference
ALTER TABLE audit_logs ALTER COLUMN actor_id DROP NOT NULL;
ALTER TABLE audit_logs
DROP CONSTRAINT audit_logs_actor_id_fkey,
    ADD CONSTRAINT audit_logs_actor_id_fkey
        FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE support_tickets ALTER COLUMN assigned_to_id DROP NOT NULL;
ALTER TABLE support_tickets
DROP CONSTRAINT support_tickets_assigned_to_id_fkey,
    ADD CONSTRAINT support_tickets_assigned_to_id_fkey
        FOREIGN KEY (assigned_to_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE promo_codes ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE promo_codes
DROP CONSTRAINT promo_codes_created_by_fkey,
    ADD CONSTRAINT promo_codes_created_by_fkey
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

-- purchaser: keep the card if the buyer's account is deleted
ALTER TABLE gift_cards ALTER COLUMN purchaser_id DROP NOT NULL;
ALTER TABLE gift_cards
DROP CONSTRAINT fk5qkx8h4gq25cjr720es4vyixy,
    ADD CONSTRAINT gift_cards_purchaser_id_fkey
        FOREIGN KEY (purchaser_id) REFERENCES users(id) ON DELETE SET NULL;