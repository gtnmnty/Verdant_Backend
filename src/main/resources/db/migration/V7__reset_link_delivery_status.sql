-- H-9: delivery status of the last staff-issued password reset / set-password link.
-- PENDING when the link is issued, SENT or FAILED once the background email send finishes.
-- NULL = no link has been issued for this account. Lets an admin see a failed send and resend.
ALTER TABLE users
    ADD COLUMN reset_link_status VARCHAR(20),
    ADD COLUMN reset_link_status_at TIMESTAMPTZ;
