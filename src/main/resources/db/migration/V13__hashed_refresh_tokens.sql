-- M-37: refresh tokens were stored (and looked up) as the raw value, so anyone with read access to the
-- database could take over every signed-in session. Now only a SHA-256 hash is stored, like password reset
-- tokens. Existing rows are converted in place, so nobody is signed out by this migration: the hash of the
-- old raw value matches what RefreshTokenService computes from the cookie (hex SHA-256 of the UTF-8 bytes).
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);
UPDATE refresh_tokens SET token_hash = encode(sha256(convert_to(token, 'UTF8')), 'hex') WHERE token_hash IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN token_hash SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_token_hash ON refresh_tokens (token_hash);
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS token;

-- jti was written but never read; dropped.
ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS jti;

-- When a token was rotated (revoked = true). A revoked token presented again after a short grace period
-- means it was copied: every session of that user is ended (reuse detection).
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;

-- Several sessions per user now (one per device), so lookups and cleanup by user need an index.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens (user_id);
