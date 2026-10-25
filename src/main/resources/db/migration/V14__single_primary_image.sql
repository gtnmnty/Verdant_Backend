-- M-40: at most one primary image per product/service. Two concurrent setPrimary (or add-as-primary) calls
-- could each leave their own image primary, and the primaryImage batch loader then failed on the duplicate
-- key for every product or service on the page.
-- 1) Repair existing duplicates: keep the primary with the lowest sort order (then oldest id), unset the rest.
UPDATE media_images m
SET is_primary = FALSE
WHERE m.is_primary = TRUE
  AND EXISTS (SELECT 1 FROM media_images keep
              WHERE keep.entity_type = m.entity_type
                AND keep.entity_id = m.entity_id
                AND keep.is_primary = TRUE
                AND (keep.sort_order < m.sort_order
                     OR (keep.sort_order = m.sort_order AND keep.id < m.id)));

-- 2) The rule itself: a partial unique index over the primary rows only.
CREATE UNIQUE INDEX IF NOT EXISTS uq_media_images_one_primary
    ON media_images (entity_type, entity_id) WHERE is_primary;
