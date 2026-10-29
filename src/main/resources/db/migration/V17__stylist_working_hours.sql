-- Stylist.workingHours (a JSON list of OperatingHours) had no column in the production dump or in any
-- migration, so schema validation failed. Existing stylists get an empty list, which is what a new Stylist
-- starts with. Guarded in case a copy of the database already has the column.
ALTER TABLE stylists ADD COLUMN IF NOT EXISTS working_hours jsonb NOT NULL DEFAULT '[]'::jsonb;
