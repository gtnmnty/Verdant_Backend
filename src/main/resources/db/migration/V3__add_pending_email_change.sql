-- Self-service email change (audit M-1).
-- The requested address waits in pending_email until the user proves they own it
-- by entering the code mailed there. Only then is users.email swapped.
-- pending_email_code holds a BCrypt hash, never the plain code.
ALTER TABLE users
    ADD COLUMN pending_email                 VARCHAR(255),
    ADD COLUMN pending_email_code            VARCHAR(255),
    ADD COLUMN pending_email_code_expiration TIMESTAMP WITH TIME ZONE;
