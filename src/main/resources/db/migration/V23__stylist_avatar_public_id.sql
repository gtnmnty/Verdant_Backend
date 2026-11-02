-- Cloudinary public id of a stylist's uploaded avatar, so replacing/removing it can delete the old file.
ALTER TABLE stylists ADD COLUMN avatar_public_id character varying(255);
