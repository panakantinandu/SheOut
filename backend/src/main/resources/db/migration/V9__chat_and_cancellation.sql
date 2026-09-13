-- Two trust-and-safety changes that share a migration because they are one
-- piece of work: taking direct contact away between a rider and a partner,
-- and making cancellations accountable.

-- ---------------------------------------------------------------------------
-- In-app chat, scoped to one booking.
--
-- Its own table in its own module rather than a column on bookings: a
-- booking has one row and a conversation has many, and chat has its own
-- lifecycle rules (writable only while the trip is live, readable forever
-- for disputes) that have nothing to do with a booking's state machine.
--
-- No phone number is ever stored here. That is not only a display rule -
-- ChatService refuses a message that looks like it carries one, because a
-- channel that quietly lets people swap numbers is the same channel we just
-- took away.
CREATE TABLE chat_messages (
    id                  UUID PRIMARY KEY,
    booking_id          UUID         NOT NULL,
    sender_account_id   UUID         NOT NULL,
    -- Denormalised so a thread can be rendered, and read by an admin during
    -- a dispute, without joining out to auth for every line.
    sender_role         VARCHAR(20)  NOT NULL,
    body                VARCHAR(1000) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);

-- Every read is "the thread for this booking, oldest first".
CREATE INDEX idx_chat_messages_booking ON chat_messages (booking_id, created_at);

COMMENT ON TABLE chat_messages IS
    'Booking-scoped chat. Writable only while the booking is ACCEPTED or IN_PROGRESS; readable afterwards so a dispute can be settled. Enforced in ChatService, not by the client.';

-- ---------------------------------------------------------------------------
-- Why a booking was cancelled, and by whom.
--
-- BookingCancelled's own Javadoc already flagged the absence of this as a
-- gap a real system would want. Without it, a cancellation is a fact with no
-- explanation: nobody can tell a rider who changed their mind from one whose
-- partner never showed up, and both were counted the same way.
ALTER TABLE bookings
    ADD COLUMN cancellation_reason VARCHAR(40),
    ADD COLUMN cancellation_note   VARCHAR(500),
    ADD COLUMN cancelled_by        UUID;

COMMENT ON COLUMN bookings.cancellation_reason IS
    'CancellationReason enum. NULL for any booking that was not cancelled.';
COMMENT ON COLUMN bookings.cancellation_note IS
    'Free text, required only when the reason is OTHER. Never shown to the other party.';
COMMENT ON COLUMN bookings.cancelled_by IS
    'The account that cancelled. Distinguishes a rider cancelling from a partner cancelling, which is the whole point of counting them separately.';

-- ---------------------------------------------------------------------------
-- Cancellation accountability, on both profile kinds.
--
-- Counters rather than a COUNT(*) at read time, because they are read on
-- every admin list row and maintained by the same event listeners that
-- already project verification state into these tables - the established
-- pattern here. They are a projection of the bookings table and that table
-- stays the source of truth.
--
-- A RATE, not just a count: a raw count punishes a rider of three years with
-- forty cancellations out of nine hundred trips, and lets a brand-new account
-- that cancelled three out of three look clean. The rate is derived from
-- these two columns rather than stored, so it cannot disagree with them.
ALTER TABLE customer_profiles
    ADD COLUMN total_bookings      INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_cancellations INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN flagged_at          TIMESTAMPTZ,
    ADD COLUMN flagged_reason      VARCHAR(500);

ALTER TABLE driver_profiles
    ADD COLUMN total_bookings      INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN total_cancellations INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN flagged_at          TIMESTAMPTZ,
    ADD COLUMN flagged_reason      VARCHAR(500);

-- Admins filter on this, and it is checked whenever a cancellation lands.
CREATE INDEX idx_customer_profiles_flagged ON customer_profiles (flagged_at);
CREATE INDEX idx_driver_profiles_flagged ON driver_profiles (flagged_at);

COMMENT ON COLUMN customer_profiles.flagged_at IS
    'Set when the cancellation rate crossed the configured threshold. A flag for a human to look at - it never blocks anyone by itself, matching how verification works here.';
COMMENT ON COLUMN driver_profiles.flagged_at IS
    'Set when the cancellation rate crossed the configured threshold. A flag for a human to look at - it never blocks anyone by itself, matching how verification works here.';
