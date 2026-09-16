-- Two unrelated additions that happen to land together: a partner's PAN, and
-- the quick tags a rating can carry.

-- ---------------------------------------------------------------------------
-- PAN, for payout tax compliance.
--
-- Optional, and deliberately not part of identity verification: a partner is
-- established as who she says she is by an operator reading her Aadhaar, and
-- nothing here changes that. This number exists because TDS has to be
-- deducted and reported against it when payouts are processed, and asking for
-- it at payout time - after she has already earned the money - is how people
-- end up waiting on paperwork for money they are owed.
--
-- Ten characters, the standard PAN shape (AAAAA9999A). Stored upper-cased and
-- unpadded so one number has one spelling.
ALTER TABLE driver_profiles ADD COLUMN pan_number VARCHAR(10);

COMMENT ON COLUMN driver_profiles.pan_number IS
    'Optional PAN for payout tax compliance (TDS). Not an identity check - see VerificationService for that.';

-- ---------------------------------------------------------------------------
-- The quick tags chosen alongside the stars.
--
-- A child table rather than a column of joined codes, because the only
-- question anyone asks of these is "how often has this been said about this
-- account lately", and that is a GROUP BY, not a LIKE over text.
--
-- The tag is stored as its enum name, not an id: a tag that is retired later
-- must still read as what it was when somebody chose it, and a row pointing at
-- a lookup table that has moved on cannot promise that.
CREATE TABLE rating_tags (
    rating_id UUID        NOT NULL REFERENCES ratings (id) ON DELETE CASCADE,
    tag       VARCHAR(40) NOT NULL,

    PRIMARY KEY (rating_id, tag)
);

-- "What has been said about this account in the last month?" - the console's
-- feedback view, which groups by account and tag over a date range.
CREATE INDEX idx_rating_tags_tag ON rating_tags (tag);

COMMENT ON TABLE rating_tags IS
    'Quick-tap reasons chosen with a rating. Deleted with the rating row; see RatingTag for the allowed values.';
