-- Campaigns: promotional credit for riders and incentives for partners.
--
-- A temporary layer ON TOP of the real fare. Nothing here changes the fare
-- formula: a booking is priced exactly as before, and a promotion then pays
-- some or all of it on the rider's behalf. Every campaign carries a budget
-- cap and stops by itself when the cap is reached.

-- ---------------------------------------------------------------- riders

create table promotions (
    id                       uuid           primary key,
    name                     varchar(120)   not null,
    -- For code-entry promotions (referral-style); null for ones granted
    -- automatically (the signup credit) or applied to everyone.
    code                     varchar(40)    unique,
    type                     varchar(30)    not null,   -- SIGNUP_CREDIT | PERCENTAGE_DISCOUNT | FLAT_DISCOUNT
    -- Rupees for SIGNUP_CREDIT and FLAT_DISCOUNT; a percentage for PERCENTAGE_DISCOUNT.
    value                    numeric(10, 2) not null,
    -- A percentage discount can be bounded per trip; null = no bound.
    max_discount_per_booking numeric(10, 2),
    max_uses_per_account     integer        not null default 1,
    -- How long a granted signup credit lasts after it is granted; null = until the campaign ends.
    credit_valid_days        integer,
    valid_from               timestamptz    not null,
    valid_until              timestamptz,
    -- What this promotion may cost in total, and what it has cost so far:
    -- discounts on live and finished trips. At the cap it stops applying.
    budget_cap               numeric(12, 2) not null,
    spent                    numeric(12, 2) not null default 0,
    paused                   boolean        not null default false,
    auto_disabled_at         timestamptz,
    created_at               timestamptz    not null,
    updated_at               timestamptz    not null
);

-- What one rider holds from one promotion: a credit balance (signup
-- credit) or a number of uses (a code she redeemed).
create table promotion_grants (
    id                uuid           primary key,
    promotion_id      uuid           not null references promotions (id),
    account_id        uuid           not null,
    granted_at        timestamptz    not null,
    expires_at        timestamptz,
    credit_total      numeric(10, 2),
    credit_remaining  numeric(10, 2),
    uses_remaining    integer,
    -- When her credit ran out (the last of it spent on a finished trip).
    exhausted_at      timestamptz,
    created_at        timestamptz    not null,
    updated_at        timestamptz    not null,
    constraint uq_promotion_grant unique (promotion_id, account_id)
);
create index idx_promotion_grants_account on promotion_grants (account_id);

-- One discount on one trip: reserved when she books, consumed when the trip
-- completes, released (and given back) when it is cancelled or finds nobody.
create table promotion_redemptions (
    id            uuid           primary key,
    promotion_id  uuid           not null references promotions (id),
    grant_id      uuid           references promotion_grants (id),
    account_id    uuid           not null,
    booking_id    uuid           not null unique,
    fare          numeric(10, 2) not null,
    discount      numeric(10, 2) not null,
    status        varchar(20)    not null,   -- RESERVED | CONSUMED | RELEASED
    created_at    timestamptz    not null,
    updated_at    timestamptz    not null
);
create index idx_promotion_redemptions_account on promotion_redemptions (account_id, promotion_id);

-- ---------------------------------------------------------------- partners

create table driver_incentives (
    id                uuid           primary key,
    name              varchar(120)   not null,
    type              varchar(40)    not null,   -- PER_TRIP_BONUS | MINIMUM_EARNINGS_GUARANTEE
    -- PER_TRIP_BONUS: rupees per trip. MINIMUM_EARNINGS_GUARANTEE: the least
    -- she earns on a trip; the difference is topped up.
    value             numeric(10, 2) not null,
    -- Only her first N completed trips qualify; null = every trip.
    first_n_trips     integer,
    valid_from        timestamptz    not null,
    valid_until       timestamptz,
    budget_cap        numeric(12, 2) not null,
    spent             numeric(12, 2) not null default 0,
    paused            boolean        not null default false,
    auto_disabled_at  timestamptz,
    created_at        timestamptz    not null,
    updated_at        timestamptz    not null
);

create table incentive_awards (
    id            uuid           primary key,
    incentive_id  uuid           not null references driver_incentives (id),
    driver_id     uuid           not null,
    booking_id    uuid           not null,
    amount        numeric(10, 2) not null,
    created_at    timestamptz    not null,
    updated_at    timestamptz    not null,
    constraint uq_incentive_award unique (incentive_id, booking_id)
);
create index idx_incentive_awards_driver on incentive_awards (driver_id);

-- ---------------------------------------------------------------- the trip and the payment

-- The fare stays the real fare; what a promotion covered, and what she pays.
alter table bookings
    add column promo_discount numeric(10, 2) not null default 0,
    add column promotion_name varchar(120);

-- A promotion lowers what the rider is charged, never what the partner earns:
-- her share is worked out from the whole fare.
alter table payments
    add column fare_amount numeric(10, 2);

-- Incentive credits in the partner's wallet, one per award.
alter table wallet_entries
    add column incentive_award_id uuid unique;

-- ---------------------------------------------------------------- the signup credit

-- ₹100 for every new rider, to spend on her trips within 30 days. The
-- amount, validity and budget are data - change them in the console's
-- Campaigns section; the budget cap below is a starting figure, not a
-- decision.
insert into promotions (id, name, code, type, value, max_uses_per_account, credit_valid_days,
                        valid_from, budget_cap, spent, paused, created_at, updated_at)
values (gen_random_uuid(), 'Signup credit', null, 'SIGNUP_CREDIT', 100.00, 1, 30,
        now(), 25000.00, 0, false, now(), now());
