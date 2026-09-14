-- Payouts: what SheOut owes its partners, and how it gets to them.
--
-- Owned by the payouts module. payments knows what riders paid; this knows
-- what each partner has earned from it, what she has been paid, and where
-- to send the rest. Driver ids are plain references to auth's accounts.

-- Where a partner is paid. Bank account OR UPI VPA, at least one before a
-- payout can be requested. Never a card: payouts do not go to cards.
CREATE TABLE payout_accounts (
    id                   UUID          PRIMARY KEY,
    driver_account_id    UUID          NOT NULL UNIQUE,
    account_holder_name  VARCHAR(100),
    account_number       VARCHAR(18),
    ifsc                 VARCHAR(11),
    upi_vpa              VARCHAR(100),
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL
);

-- One running balance per partner. Every figure here is also the sum of her
-- wallet_entries; the row exists so reading a balance, and locking it while
-- a payout is requested, is one row rather than a sum over her history.
--
-- available = total_earned - cash_collected - total_paid_out - pending_payouts
--
-- cash_collected is what riders handed her directly. It is subtracted
-- because that money is already in her pocket; subtracting the whole fare
-- while total_earned holds only her share is exactly how the platform's
-- commission on a cash trip ends up owed by her rather than paid twice.
CREATE TABLE driver_wallets (
    id                 UUID           PRIMARY KEY,
    driver_account_id  UUID           NOT NULL UNIQUE,
    total_earned       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    cash_collected     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_paid_out     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    pending_payouts    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL
);

-- Every movement, append-only - the explanation behind each wallet figure.
-- Unique on (payment_id, entry_type): a capture credits a wallet once,
-- however many times the event is delivered.
CREATE TABLE wallet_entries (
    id                 UUID           PRIMARY KEY,
    driver_account_id  UUID           NOT NULL,
    entry_type         VARCHAR(30)    NOT NULL,
    -- Signed: positive adds to what she can withdraw, negative takes from it.
    amount             NUMERIC(12, 2) NOT NULL,
    booking_id         UUID,
    payment_id         UUID,
    payout_request_id  UUID,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_wallet_entries_payment UNIQUE (payment_id, entry_type)
);

CREATE INDEX idx_wallet_entries_driver ON wallet_entries (driver_account_id, created_at DESC);

-- A partner asking to be paid. The destination is copied onto the request
-- when she asks: the operator pays what she asked to be paid to, and a later
-- change of bank details cannot redirect money already requested.
CREATE TABLE payout_requests (
    id                   UUID           PRIMARY KEY,
    driver_account_id    UUID           NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    account_holder_name  VARCHAR(100),
    account_number       VARCHAR(18),
    ifsc                 VARCHAR(11),
    upi_vpa              VARCHAR(100),
    -- Same audit convention as sos_alert: who marked it paid, when, and the
    -- bank or UPI reference that proves it.
    paid_at              TIMESTAMPTZ,
    paid_by              UUID,
    payment_reference    VARCHAR(100),
    created_at           TIMESTAMPTZ    NOT NULL,
    updated_at           TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_payout_requests_status ON payout_requests (status, created_at);
CREATE INDEX idx_payout_requests_driver ON payout_requests (driver_account_id, created_at DESC);
