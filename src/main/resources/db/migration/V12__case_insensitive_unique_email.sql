-- M-36: an email address is unique regardless of case or surrounding spaces. Before, "Jane@x.com" and
-- "jane@x.com" could both register, and login only worked with the exact case used at signup.
-- 1) Refuse to continue if existing accounts collide once normalized. Merging two accounts is a business
--    decision (orders, bookings, reviews hang off each), so the migration stops and names the problem
--    instead of guessing. Fix it by hand (e.g. rename one address), then restart the app.
DO $$
DECLARE
    clash TEXT;
BEGIN
    SELECT string_agg(normalized, ', ') INTO clash
    FROM (SELECT lower(trim(email)) AS normalized
          FROM users
          GROUP BY lower(trim(email))
          HAVING COUNT(*) > 1) d;
    IF clash IS NOT NULL THEN
        RAISE EXCEPTION 'M-36: these emails belong to more than one account when case is ignored: %. '
            'Resolve them by hand, then restart.', clash;
    END IF;
END $$;

-- 2) Store every address normalized (trim + lower-case), the same way EmailNormalizer does in the app.
UPDATE users SET email = lower(trim(email)) WHERE email <> lower(trim(email));
UPDATE users SET pending_email = lower(trim(pending_email))
WHERE pending_email IS NOT NULL AND pending_email <> lower(trim(pending_email));

-- 3) Case-insensitive uniqueness. UserRepository looks users up with lower(email) = ?, which uses this index.
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (lower(email));
