-- Two-way ratings, and the trust signal they feed.

-- ---------------------------------------------------------------------------
-- One row per person who is entitled to rate one trip.
--
-- The row is created empty when the trip completes, not when somebody rates.
-- That is what makes "have I already rated this?" and "is there anything
-- waiting for me?" single cheap questions, and it is what pins the closing
-- time to the moment the trip actually ended rather than recomputing it from
-- a booking every time somebody asks.
--
-- stars NULL means the slot is still open. It is the only nullable thing here
-- that carries meaning, and the partial indexes below lean on it.
CREATE TABLE ratings (
    id                UUID PRIMARY KEY,
    booking_id        UUID         NOT NULL,
    rater_account_id  UUID         NOT NULL,
    rated_account_id  UUID         NOT NULL,
    -- Which side of the trip the rater was. Denormalised so a rating can be
    -- read and attributed without joining out to auth.
    rater_role        VARCHAR(20)  NOT NULL,
    stars             INTEGER,
    comment           VARCHAR(500),
    submitted_at      TIMESTAMPTZ,
    -- After this, the slot closes for good. Stored rather than derived so
    -- changing RATING_WINDOW_HOURS cannot silently reopen trips that have
    -- already closed, or shut ones that a person was told they could still
    -- rate.
    rateable_until    TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_ratings_stars CHECK (stars IS NULL OR (stars BETWEEN 1 AND 5))
);

-- Each side rates a trip once. This constraint is the real guard, not the
-- check in the service: two requests arriving together would both pass a
-- read-then-write check and the second would quietly overwrite the first.
CREATE UNIQUE INDEX uq_ratings_booking_rater ON ratings (booking_id, rater_account_id);

-- "What is this account's average?" - only submitted rows count.
CREATE INDEX idx_ratings_rated ON ratings (rated_account_id) WHERE stars IS NOT NULL;

-- "Is anything waiting for me to rate?"
CREATE INDEX idx_ratings_rater_pending ON ratings (rater_account_id) WHERE stars IS NULL;

COMMENT ON TABLE ratings IS
    'One slot per participant per completed booking. Created empty on BookingCompleted; filled in once, within the configured window. The unique index is what enforces "once", not application code.';
COMMENT ON COLUMN ratings.rateable_until IS
    'Frozen at slot creation from RATING_WINDOW_HOURS. A trip nobody remembers is a trip nobody can rate honestly.';

-- ---------------------------------------------------------------------------
-- The rating average, projected onto both profile kinds.
--
-- Same shape and same reasoning as the cancellation counters beside it: the
-- ratings table stays the source of truth, and these are maintained by an
-- event listener so the figures an operator reads, and the figure the trust
-- check runs against, come from one place.
--
-- They sit on the profile rather than being computed per read because the
-- trust flag is decided from the rating average AND the cancellation rate
-- together - a single decision needs both numbers in one row.
ALTER TABLE customer_profiles
    ADD COLUMN average_stars  NUMERIC(3, 2),
    ADD COLUMN total_ratings  INTEGER NOT NULL DEFAULT 0;

ALTER TABLE driver_profiles
    ADD COLUMN average_stars  NUMERIC(3, 2),
    ADD COLUMN total_ratings  INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN customer_profiles.average_stars IS
    'NULL until the account has been rated at all. Distinguishes "nobody has rated her" from a genuine low score - showing 0.00 for a new account would be a lie about her.';
COMMENT ON COLUMN driver_profiles.average_stars IS
    'NULL until the account has been rated at all. Distinguishes "nobody has rated her" from a genuine low score.';
