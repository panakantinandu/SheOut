-- A trip is not over until it is paid, and it is paid only through SheOut.
--
-- Two things change here:
--
-- 1. bookings.payment_settled_at. A trip the partner has ended (COMPLETED)
--    stays unsettled until the rider's payment is captured. While it is, the
--    rider cannot book again and, for a grace period, the partner is not
--    offered new work. Owned by booking; payments sets it when it captures.
--
-- 2. A rider wallet. A closed-loop SheOut balance: money comes in only from a
--    Razorpay top-up and goes out only to pay for her own trips. No transfers
--    to other people and no withdrawal, which keeps it a closed system
--    prepaid instrument under RBI rules rather than a licensed wallet.

ALTER TABLE bookings ADD COLUMN payment_settled_at timestamp;

-- Trips finished before this release are treated as settled. They were
-- completed under the old rules, when a partner could close a trip and
-- confirm cash, and nearly all of them are test trips on accounts that would
-- otherwise be locked out of booking by fares nobody is going to pay. Their
-- payment rows are marked WAIVED with the reason, not deleted, so the record
-- of what was and was not paid survives.
UPDATE bookings b
   SET payment_settled_at = COALESCE(p.captured_at, now())
  FROM payments p
 WHERE p.booking_id = b.id
   AND b.status = 'COMPLETED';

UPDATE bookings
   SET payment_settled_at = now()
 WHERE status = 'COMPLETED'
   AND payment_settled_at IS NULL;

UPDATE payments
   SET status = 'WAIVED',
       failure_reason = 'Trip ended before payment was required to close it'
 WHERE status IN ('PENDING', 'FAILED');

-- The two questions asked on every booking request and every dispatch
-- offer: does this rider, or this partner, have a trip still unpaid?
CREATE INDEX idx_bookings_customer_unsettled
    ON bookings (customer_id)
 WHERE status = 'COMPLETED' AND payment_settled_at IS NULL;

CREATE INDEX idx_bookings_driver_unsettled
    ON bookings (driver_id, completed_at)
 WHERE status = 'COMPLETED' AND payment_settled_at IS NULL;

-- One balance per rider. The CHECK is the last line of defence against a
-- double spend: two trip payments racing on the same balance cannot both
-- succeed, because the second would take it below zero and the database
-- refuses it even if the application lock were ever bypassed.
CREATE TABLE rider_wallets (
    id                  UUID           PRIMARY KEY,
    customer_account_id UUID           NOT NULL UNIQUE,
    balance             NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL
);

-- A top-up she started. Money is credited only when Razorpay confirms the
-- capture - through Checkout's verify call or the webhook, whichever lands
-- first - and at most once: the order id and the payment id are both unique.
CREATE TABLE wallet_topups (
    id                  UUID           PRIMARY KEY,
    customer_account_id UUID           NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status              VARCHAR(20)    NOT NULL,
    razorpay_order_id   VARCHAR(255)   UNIQUE,
    razorpay_payment_id VARCHAR(255)   UNIQUE,
    method              VARCHAR(20),
    failure_reason      VARCHAR(500),
    captured_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_wallet_topups_customer ON wallet_topups (customer_account_id, created_at DESC);

-- Every movement on a rider's balance, append-only. balance_after is the
-- balance once this entry applied, so a statement can be read top to bottom
-- without re-adding anything.
CREATE TABLE rider_wallet_entries (
    id                  UUID           PRIMARY KEY,
    customer_account_id UUID           NOT NULL,
    entry_type          VARCHAR(30)    NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    balance_after       NUMERIC(12, 2) NOT NULL,
    booking_id          UUID,
    topup_id            UUID,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_rider_wallet_entries_customer ON rider_wallet_entries (customer_account_id, created_at DESC);

-- A top-up credits once and a trip is paid from the wallet once, however
-- many times a request is retried or a webhook redelivered.
CREATE UNIQUE INDEX uq_rider_wallet_entries_topup
    ON rider_wallet_entries (topup_id) WHERE entry_type = 'TOPUP';
CREATE UNIQUE INDEX uq_rider_wallet_entries_trip
    ON rider_wallet_entries (booking_id) WHERE entry_type = 'TRIP_PAYMENT';
