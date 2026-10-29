-- reviews_target_type_check (from the hand-built schema) allows 'PRODUCT' and 'SERVICE', but Review.targetType
-- is the ItemType enum, which writes 'PRODUCT' and 'SALON_SERVICE'. Every salon-service review hit the check,
-- and ReviewService turned that DataIntegrityViolation into "You have already reviewed this" (a 409).
-- Production had no reviews when this was found. Any legacy 'SERVICE' rows are renamed so they keep counting
-- (V10's aggregate recompute and ReviewService both look for 'SALON_SERVICE').
ALTER TABLE reviews DROP CONSTRAINT IF EXISTS reviews_target_type_check;
UPDATE reviews SET target_type = 'SALON_SERVICE' WHERE target_type = 'SERVICE';
ALTER TABLE reviews ADD CONSTRAINT reviews_target_type_check
    CHECK (target_type IN ('PRODUCT', 'SALON_SERVICE'));
