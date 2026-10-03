-- Staff accounts: the people who run the console, signed in with a work
-- email, a password and an authenticator code - never a phone and an SMS.
--
-- Until now an operator was an ADMIN account signed in exactly like a rider,
-- and every operator could do everything. One stolen SIM or phished code
-- opened every woman's documents, addresses, live location and payouts.
--
-- Each member of staff is backed by an accounts row (role ADMIN, no phone, no
-- email) because every module already records operator decisions by account
-- id; see staff's package-info. Everything that is a credential lives here.

create table staff_members (
    id                  uuid primary key,
    -- The ADMIN account standing for her in other modules' "who did this"
    -- columns. Unique: one person, one id.
    account_id          uuid         not null unique references accounts (id),
    -- Always stored lowercased (see StaffEmail); unique below.
    email               varchar(254) not null,
    display_name        varchar(80)  not null,
    role                varchar(30)  not null,
    -- ACTIVE or DISABLED. Disabled is reversible, but only by an OWNER.
    status              varchar(20)  not null,
    -- Argon2id, in Spring Security's encoded form (parameters and salt inside).
    password_hash       varchar(200) not null,
    password_changed_at timestamptz  not null,
    -- The authenticator secret, encrypted with STAFF_SECRETS_KEY (AES-GCM).
    -- A copy of this table alone cannot produce a code.
    totp_secret         varchar(200) not null,
    -- The newest 30-second step a code was accepted for, so a code seen over
    -- someone's shoulder cannot be used a second time.
    totp_last_step      bigint,
    failed_login_count  integer      not null default 0,
    locked_until        timestamptz,
    -- AUDITOR only: access ends on its own.
    access_expires_at   timestamptz,
    last_login_at       timestamptz,
    invited_by          uuid,
    disabled_at         timestamptz,
    disabled_by         uuid,
    disabled_reason     varchar(500),
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    constraint chk_staff_role check (role in ('OWNER', 'MANAGER', 'VERIFICATION_AGENT', 'SUPPORT_AGENT',
        'SAFETY_RESPONDER', 'FINANCE', 'MARKETPLACE_MODERATOR', 'AUDITOR')),
    constraint chk_staff_status check (status in ('ACTIVE', 'DISABLED')),
    constraint chk_staff_auditor_expires check (role <> 'AUDITOR' or access_expires_at is not null)
);

create unique index uq_staff_members_email on staff_members (lower(email));

-- Ten single-use codes for a lost phone. Only an HMAC of each is kept.
create table staff_recovery_codes (
    id          uuid primary key,
    staff_id    uuid        not null references staff_members (id) on delete cascade,
    code_hash   varchar(64) not null,
    created_at  timestamptz not null,
    used_at     timestamptz
);

create index idx_staff_recovery_codes_staff on staff_recovery_codes (staff_id);

-- Invitations. Nobody signs up: an OWNER or MANAGER invites by email, and the
-- link works once, for 24 hours. The same table carries a second-factor reset
-- (staff_member_id set): the link lets her set a new password and
-- authenticator for the account she already has.
create table staff_invites (
    id                    uuid primary key,
    -- SHA-256 of the token in the link. The token itself is never stored.
    token_hash            varchar(64)  not null unique,
    email                 varchar(254) not null,
    display_name          varchar(80)  not null,
    role                  varchar(30)  not null,
    access_expires_at     timestamptz,
    -- The staff member who sent it; null for the bootstrap invite.
    invited_by            uuid,
    -- Set for a second-factor reset of an existing member.
    staff_member_id       uuid references staff_members (id),
    -- Set when the first OWNER takes over an existing phone-login ADMIN
    -- account, so her earlier decisions stay attributed to her.
    link_account_id       uuid references accounts (id),
    -- PENDING, USED or REVOKED.
    status                varchar(20)  not null,
    expires_at            timestamptz  not null,
    -- Between choosing a password and confirming the authenticator. Cleared
    -- when the invite is used or revoked.
    pending_password_hash varchar(200),
    pending_totp_secret   varchar(200),
    created_at            timestamptz  not null,
    used_at               timestamptz,
    revoked_at            timestamptz,
    constraint chk_staff_invite_status check (status in ('PENDING', 'USED', 'REVOKED'))
);

create index idx_staff_invites_email_status on staff_invites (lower(email), status);

-- A console sign-in. Each one is also an account_sessions row, so the
-- existing machinery that lists, revokes and checks sessions covers staff
-- too; this row holds what only a cookie session needs.
create table staff_sessions (
    session_id            uuid primary key references account_sessions (id) on delete cascade,
    staff_id              uuid        not null references staff_members (id),
    -- SHA-256 of the cookie's value. The value itself is never stored.
    token_hash            varchar(64) not null unique,
    -- Sent back by the console on every change, checked against this.
    csrf_token            varchar(64) not null,
    ip_address            varchar(64),
    created_at            timestamptz not null,
    -- Last time the person did something, not when the page last polled.
    last_activity_at      timestamptz not null,
    idle_timeout_minutes  integer     not null,
    absolute_expires_at   timestamptz not null,
    -- The SOS push registration made from this browser, if any, so signing
    -- out stops that browser receiving the next alert.
    push_token            varchar(500)
);

create index idx_staff_sessions_staff on staff_sessions (staff_id);
