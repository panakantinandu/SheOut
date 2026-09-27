-- Refer a friend. See ReferralService.
--
-- The rewards are ordinary campaigns - promotions for riders, incentives for
-- partners - so they carry a budget cap, stop by themselves at it, and are
-- edited and paused in the console's Campaigns section like any other. This
-- migration adds only who referred whom.

-- One code per account, made at signup (or the first time she opens Refer a
-- Friend, for accounts from before this). A code belongs to one app: a
-- rider's brings riders, a partner's brings partners.
create table referral_codes (
    id          uuid         primary key,
    account_id  uuid         not null unique,
    role        varchar(20)  not null,
    code        varchar(12)  not null unique,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);

-- A new account that signed up with somebody's code. PENDING until the new
-- account's first paid trip - taken and paid for by a rider, or driven and
-- paid for by a partner - then COMPLETED with what each side was given, or
-- REJECTED when the two turn out to be the same person. One per new account.
create table referrals (
    id                   uuid           primary key,
    referrer_account_id  uuid           not null,
    referee_account_id   uuid           not null unique,
    role                 varchar(20)    not null,
    code                 varchar(12)    not null,
    status               varchar(20)    not null,
    qualifying_booking_id uuid,
    completed_at         timestamptz,
    -- What each side was given; zero when the campaign was not running or
    -- had no budget left, or (referrer) she had reached the per-person limit.
    referrer_reward      numeric(10, 2),
    referee_reward       numeric(10, 2),
    note                 varchar(300),
    created_at           timestamptz    not null,
    updated_at           timestamptz    not null
);
create index idx_referrals_referrer on referrals (referrer_account_id, status);

-- Which app installs an account has used the referral screens from - a
-- random id the app keeps in its own storage. A friend applying a code from
-- the same install the code's owner shared it from is the owner.
create table referral_devices (
    id          uuid         primary key,
    account_id  uuid         not null,
    install_id  varchar(64)  not null,
    last_seen_at timestamptz not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    constraint uq_referral_device unique (account_id, install_id)
);
create index idx_referral_devices_install on referral_devices (install_id);

-- The rewards. Starting figures, not decisions: amounts, validity and budgets
-- are edited in the console.
--   Riders: Rs 50 credit to each side, spendable for 60 days.
--   Partners: Rs 100 into each side's wallet.
insert into promotions (id, name, code, type, value, max_uses_per_account, credit_valid_days,
                        valid_from, budget_cap, spent, paused, created_at, updated_at)
values (gen_random_uuid(), 'Referral reward', null, 'REFERRAL_REWARD', 50.00, 1, 60,
        now(), 10000.00, 0, false, now(), now()),
       (gen_random_uuid(), 'Referral welcome', null, 'REFERRAL_WELCOME', 50.00, 1, 60,
        now(), 10000.00, 0, false, now(), now());

insert into driver_incentives (id, name, type, value, first_n_trips, valid_from, budget_cap, spent, paused,
                               created_at, updated_at)
values (gen_random_uuid(), 'Partner referral reward', 'REFERRAL_REWARD', 100.00, null, now(), 10000.00, 0, false, now(), now()),
       (gen_random_uuid(), 'Partner referral welcome', 'REFERRAL_WELCOME', 100.00, null, now(), 10000.00, 0, false, now(), now());
