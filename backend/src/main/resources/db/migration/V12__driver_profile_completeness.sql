-- What a rider is shown about the person picking her up, and what an
-- operator reads before letting that person work.

-- ---------------------------------------------------------------------------
-- A partner's profile photo.
--
-- Stored as an opaque storage key, not a URL - the same shape as the
-- Aadhaar key next door. A key survives moving from local disk to S3, or
-- from one bucket to another; a baked-in URL does not, and a presigned URL
-- would be stale within the hour.
--
-- Lives on the profile rather than with the verification documents, because
-- it is not review evidence: it is what a rider sees when she is deciding
-- whether the woman in front of her is the one the app sent. The Aadhaar
-- and RC images are the opposite - an operator reads them once and a rider
-- must never see them at all.
ALTER TABLE driver_profiles ADD COLUMN profile_photo_key VARCHAR(500);

COMMENT ON COLUMN driver_profiles.profile_photo_key IS
    'DocumentStorage key for the partner photo shown to a rider. NULL for accounts created before this was required; the apps render a silhouette rather than a broken image.';

-- ---------------------------------------------------------------------------
-- The registration certificate photo.
--
-- On the verification record, next to the Aadhaar key, and deliberately NOT
-- on driver_profiles beside the photo above. It is evidence for one review
-- decision: an operator reads the Aadhaar to establish who she is and the
-- RC to check the number she typed matches the vehicle she actually owns.
-- Splitting a single decision's evidence across two tables in two modules
-- would mean the review queue had to join them back together to show what
-- it already needs to show side by side.
ALTER TABLE verification_records ADD COLUMN rc_document_key VARCHAR(500);

COMMENT ON COLUMN verification_records.rc_document_key IS
    'DocumentStorage key for the vehicle registration certificate photo. Read by an operator alongside the Aadhaar document to cross-check the typed registration number. Never shown to a rider.';
