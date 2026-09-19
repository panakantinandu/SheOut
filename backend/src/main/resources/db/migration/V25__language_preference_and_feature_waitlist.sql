-- Two small per-account records, owned by the users module.
--
-- account_preferences: the language she chose in the app, so it follows her
-- to another phone or a fresh sign-in instead of resetting to whatever the
-- new browser happens to be set to. One row per account, either role.
CREATE TABLE account_preferences (
    id          UUID        PRIMARY KEY,
    account_id  UUID        NOT NULL UNIQUE,
    language    VARCHAR(5)  NOT NULL CHECK (language IN ('en', 'te', 'hi')),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

-- feature_waitlist: "notify me when this launches", for features that do
-- not exist yet. Deliberately just who and when - it is a measure of demand
-- before anything is built, not a notification system. The unique key makes
-- tapping the button twice one signal, not two.
CREATE TABLE feature_waitlist (
    id          UUID        PRIMARY KEY,
    account_id  UUID        NOT NULL,
    feature     VARCHAR(30) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_feature_waitlist UNIQUE (account_id, feature)
);

CREATE INDEX idx_feature_waitlist_feature ON feature_waitlist (feature, created_at);

-- The seeded Help answer still said riders pay "by UPI or cash". Cash has not
-- been accepted since V23. Only the untouched seed is corrected; a value an
-- operator has already edited is theirs and is left alone.
UPDATE content_blocks
   SET value = 'Per trip, in the app - from your SheOut wallet, or online by UPI, card or netbanking. Cash is not accepted. See Payment History for what you have paid.',
       updated_at = now()
 WHERE content_key = 'faq.customer.5.answer'
   AND value = 'Per trip, by UPI or cash. No card is stored on your account - see Payment History for what you have paid.';

-- The partner answers had gone stale the same way: payouts exist now, and a
-- partner's requests pause while her last rider's payment is pending (V23).
UPDATE content_blocks
   SET value = 'Your earnings are your share of every trip your riders have paid for. Each paid fare is added to your SheOut wallet, and you can request a payout to your bank or UPI from Wallet & payouts.',
       updated_at = now()
 WHERE content_key = 'faq.driver.6.answer'
   AND value = 'Earnings are the total of your completed trips. There is no separate payout module yet, so Earnings shows trip totals rather than settled payouts.';

UPDATE content_blocks
   SET value = 'You must be online, verified, and allowing location access - requests are offered to the nearest available partners first, then further out. Requests also pause for up to ten minutes after a trip while the rider pays.',
       updated_at = now()
 WHERE content_key = 'faq.driver.3.answer'
   AND value = 'You must be online, verified, and allowing location access - requests are offered to the nearest available partners first, then further out.';
