-- Proof that the woman getting into the vehicle is the woman who booked it.

-- ---------------------------------------------------------------------------
-- The pickup code.
--
-- Four digits, generated when a partner ACCEPTS, shown only to the rider,
-- and read aloud to the partner at the kerb. The partner types it to start
-- the trip. Until now "Start Trip" was an unverified tap: a partner could
-- start and complete a trip with nobody in the vehicle, and the rider was
-- charged for it.
--
-- VARCHAR, not an integer column. "0042" is a valid code and must stay four
-- characters; stored as a number it comes back as 42, and the rider reads
-- out four digits the partner cannot type.
--
-- Not hashed. That is a deliberate decision, not an oversight: this is a
-- short-lived, four-digit, single-booking secret that the server itself has
-- to show to the rider in plaintext on her tracking screen. A hash it must
-- reverse to display protects nothing, and a 10,000-entry rainbow table for
-- four digits is a rounding error. What actually limits guessing is the
-- attempt counter below.
ALTER TABLE bookings ADD COLUMN pickup_otp VARCHAR(4);

COMMENT ON COLUMN bookings.pickup_otp IS
    'Four-digit code the rider reads to her partner at pickup. Set on ACCEPTED, released only to the customer, verified before ACCEPTED -> IN_PROGRESS. NULL on bookings that never reached ACCEPTED, and on those accepted before this column existed.';

-- When the code was accepted. Distinct from started_at, which is set in the
-- same transaction today but answers a different question - one is "when
-- did the trip begin", the other is "was this trip ever verified at all".
-- Bookings accepted before this change will have started_at and no
-- pickup_verified_at, and that difference should stay legible.
ALTER TABLE bookings ADD COLUMN pickup_verified_at TIMESTAMPTZ;

COMMENT ON COLUMN bookings.pickup_verified_at IS
    'When the partner entered the correct pickup code. NULL means this trip started without verification - either it predates the check, or it never started.';

-- ---------------------------------------------------------------------------
-- Wrong guesses.
--
-- Four digits is 10,000 possibilities, which is nothing to a script and
-- perfectly adequate against a person reading a number off a phone. Without
-- a limit, a partner could start a trip - and bill a rider - for a rider who
-- was never there, by guessing. With a limit, she cannot, and a partner who
-- genuinely mistyped has several tries before she has to call support.
--
-- Counts up rather than down so the number in the column is the number of
-- wrong attempts, which is what an operator reading this row wants to know.
ALTER TABLE bookings ADD COLUMN pickup_attempts INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN bookings.pickup_attempts IS
    'Failed pickup-code attempts. Locked out at the limit in PickupCode; an operator seeing a high value here should look at why.';
