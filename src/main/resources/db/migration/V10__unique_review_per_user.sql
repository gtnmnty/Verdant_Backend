-- M-32: one review per user per product/service. ReviewService does find-then-insert, and with no unique
-- constraint two parallel submissions could both insert. The service now also locks the target row, and this
-- index is the backstop.
-- 1) Remove duplicates that may already exist, keeping each user's newest review of a target (the app
--    always meant a second submission to replace the first; "upsert" semantics).
DELETE FROM reviews r
USING reviews newer
WHERE r.user_id = newer.user_id
  AND r.target_type = newer.target_type
  AND r.target_id = newer.target_id
  AND (r.created_at < newer.created_at
       OR (r.created_at = newer.created_at AND r.id < newer.id)
       OR (r.created_at IS NULL AND newer.created_at IS NOT NULL));

-- 2) Recompute the stored aggregates, since duplicates were counted in them.
UPDATE products p
SET review_count   = COALESCE(s.cnt, 0),
    average_rating = COALESCE(s.avg_stars, 0)
FROM (SELECT pr.id, agg.cnt, agg.avg_stars
      FROM products pr
      LEFT JOIN (SELECT target_id, COUNT(*) AS cnt, ROUND(AVG(stars)::numeric, 2) AS avg_stars
                 FROM reviews WHERE target_type = 'PRODUCT' GROUP BY target_id) agg
             ON agg.target_id = pr.id) s
WHERE p.id = s.id;

UPDATE salon_services sv
SET review_count   = COALESCE(s.cnt, 0),
    average_rating = COALESCE(s.avg_stars, 0)
FROM (SELECT ss.id, agg.cnt, agg.avg_stars
      FROM salon_services ss
      LEFT JOIN (SELECT target_id, COUNT(*) AS cnt, ROUND(AVG(stars)::numeric, 2) AS avg_stars
                 FROM reviews WHERE target_type = 'SALON_SERVICE' GROUP BY target_id) agg
             ON agg.target_id = ss.id) s
WHERE sv.id = s.id;

-- 3) The constraint itself (name matches the @UniqueConstraint on Review).
CREATE UNIQUE INDEX IF NOT EXISTS uq_reviews_user_target ON reviews (user_id, target_type, target_id);
