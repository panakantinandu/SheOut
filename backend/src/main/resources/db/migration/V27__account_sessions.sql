-- Sessions, so a token can be taken away before it expires.
--
-- Until now a signed token was trusted on its own for its full thirty days.
-- Blocking an account stopped the next sign-in and nothing else: the phone
-- already holding a token carried on working, verified live against
-- production. There was also no way to end one device's session - a partner
-- signing in on a second phone left the first one live.
--
-- Every sign-in now writes a row here and the token carries its id. Each
-- authenticated request checks this row, which is one lookup by primary key,
-- the same count of queries the filter made before.
--
-- The token itself is NOT stored - only its session id. A leaked dump of this
-- table cannot be replayed as anybody's credentials.
create table account_sessions (
    id              uuid primary key,
    account_id      uuid        not null references accounts (id) on delete cascade,
    role            varchar(20) not null,
    status          varchar(20) not null,
    -- What she would recognise in a list of her own devices.
    device_label    varchar(120),
    user_agent      varchar(400),
    created_at      timestamptz not null,
    last_active_at  timestamptz not null,
    revoked_at      timestamptz,
    -- SIGNED_IN_ELSEWHERE, ACCOUNT_BLOCKED, SIGNED_OUT, ACCOUNT_DELETED.
    -- What the other device is told, rather than a silent failure.
    revoked_reason  varchar(40)
);

-- The two reads this table gets: one session by id on every request (the
-- primary key), and "this account's live sessions" when signing in on a new
-- device, blocking an account, or listing her devices.
create index idx_sessions_account_status on account_sessions (account_id, status);
