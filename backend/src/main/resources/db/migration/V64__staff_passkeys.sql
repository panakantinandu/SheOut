-- Staff passkeys (WebAuthn): a key pair kept by her phone, laptop or
-- security key, unlocked by her fingerprint, face or PIN. Required for
-- owners, recommended for managers (sheout.staff.passkey.*).
--
-- Only the PUBLIC key is stored. A copy of this table lets nobody sign in:
-- it can check a signature, never make one.
create table staff_passkeys (
    id               uuid primary key,
    staff_id         uuid          not null references staff_members (id) on delete cascade,
    -- base64url, as the browser reports it. Unique across everyone: a
    -- credential belongs to exactly one person.
    credential_id    varchar(1400) not null unique,
    -- COSE-encoded public key, base64url.
    public_key_cose  text          not null,
    -- The authenticator's own counter, to notice a cloned key. Many passkeys
    -- (synced ones) always report 0, which the library allows.
    signature_count  bigint        not null,
    -- Her own name for it ("Work laptop"), so she can tell which to remove.
    label            varchar(60)   not null,
    created_at       timestamptz   not null,
    last_used_at     timestamptz
);

create index idx_staff_passkeys_staff on staff_passkeys (staff_id);

-- One ceremony in flight: the exact options sent to the browser, so the
-- answer is checked against the challenge this server issued and nothing
-- else. Used once (deleted on use) and short-lived.
create table staff_passkey_challenges (
    id            uuid primary key,
    -- Null for a sign-in: nobody is signed in yet, and the passkey itself
    -- says whose it is.
    staff_id      uuid         references staff_members (id) on delete cascade,
    purpose       varchar(20)  not null,
    request_json  text         not null,
    expires_at    timestamptz  not null,
    constraint chk_passkey_challenge_purpose check (purpose in ('REGISTER', 'SIGN_IN', 'STEP_UP'))
);

create index idx_staff_passkey_challenges_expiry on staff_passkey_challenges (expires_at);
