-- Data rights: account deletion as deactivation plus anonymisation, never a
-- hard delete. Bookings and payments stay (tax and dispute retention) with
-- the account holder's identity already removed from every module that
-- held it.

-- When the account holder deleted it. Set once, never cleared. The row
-- stays so the bookings and payments that reference its id stay intact;
-- phone_number and email are nulled at the same moment, which is what
-- unlinks the person from the id.
ALTER TABLE accounts ADD COLUMN deleted_at TIMESTAMPTZ;

-- The compliance record of every deletion: who asked, when, and when it
-- finished. Owned by the privacy module. Deliberately holds no personal data
-- of its own - only the account id, which after deletion identifies nobody.
CREATE TABLE account_deletion_log (
    id              UUID         PRIMARY KEY,
    account_id      UUID         NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    -- Same audit convention as sos_alert and verification_records: who and
    -- when, set together. requested_by is the account itself today; the
    -- column exists so an operator-initiated deletion later records itself
    -- honestly rather than looking self-service.
    requested_by    UUID         NOT NULL,
    requested_at    TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_account_deletion_log_account ON account_deletion_log (account_id);
