-- Insurance: the master policies SheOut holds, which trip each one covered,
-- and which partners are enrolled in a group cover. See com.sheout.insurance.
--
-- Nothing here is hardcoded: every policy is created by an operator from
-- the insurer's schedule. With no active passenger policy no trip gets a
-- coverage row, and the apps say nothing about insurance.

create table insurance_policies (
    id                   uuid primary key,
    -- PASSENGER_TRIP, PARTNER_HEALTH, PARTNER_TERM_LIFE, PARTNER_ACCIDENT, GOODS_IN_TRANSIT
    kind                 varchar(30)    not null,
    insurer_name         varchar(150)   not null,
    master_policy_number varchar(80)    not null,
    sum_insured          numeric(14, 2) not null,
    premium_per_unit     numeric(10, 2) not null,
    -- PER_TRIP or PER_MEMBER_PER_YEAR
    premium_unit         varchar(30)    not null,
    effective_from       date           not null,
    effective_to         date,
    claims_phone         varchar(30),
    claims_url           varchar(500),
    policy_summary_url   varchar(500),
    -- What is covered and how to claim, in the insurer's schedule's own
    -- terms, for the rider's "Insured trip" sheet and the partner's card.
    coverage_summary     varchar(2000),
    claim_steps          varchar(2000),
    active               boolean        not null default false,
    created_by           uuid,
    created_at           timestamptz    not null,
    updated_at           timestamptz    not null
);

create index idx_insurance_policies_kind on insurance_policies (kind, active);

-- One row per trip a policy covered, opened when the trip starts and closed
-- when it ends. premium_amount is a platform cost: it is never added to the
-- rider's fare and never taken from the partner's share.
create table trip_coverages (
    id                  uuid primary key,
    booking_id          uuid           not null unique,
    policy_id           uuid           not null references insurance_policies (id),
    rider_account_id    uuid           not null,
    partner_account_id  uuid,
    category            varchar(20)    not null,
    -- Coarse place names only (locality, city): enough for an insurer to
    -- place a claim, not her house number.
    pickup_area         varchar(200),
    drop_area           varchar(200),
    coverage_started_at timestamptz    not null,
    coverage_ended_at   timestamptz,
    premium_amount      numeric(10, 2) not null,
    reported_status     varchar(20)    not null,
    reported_at         timestamptz,
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null
);

create index idx_trip_coverages_started on trip_coverages (coverage_started_at);
create index idx_trip_coverages_reported on trip_coverages (reported_status, coverage_started_at);

-- "Report an accident / make a claim" on a trip: the support ticket it
-- raised, beside the coverage it is about. SheOut helps; the insurer decides.
create table trip_coverage_claims (
    id               uuid primary key,
    booking_id       uuid         not null,
    coverage_id      uuid         references trip_coverages (id),
    ticket_id        uuid         not null,
    raised_by        uuid         not null,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null
);

create index idx_trip_coverage_claims_booking on trip_coverage_claims (booking_id);

-- A partner in a group cover (health, term life, accident). Created as
-- PENDING_ENROLMENT when she is verified; an operator marks it ENROLLED once
-- the insurer confirms her membership, and it is EXITED when her account is
-- deleted or blocked. The partner app shows a cover only when ENROLLED.
create table partner_insurance_enrolments (
    id          uuid primary key,
    account_id  uuid         not null,
    policy_id   uuid         not null references insurance_policies (id),
    member_id   varchar(80),
    status      varchar(30)  not null,
    enrolled_on date,
    exited_on   date,
    exit_reason varchar(200),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);

create unique index uq_partner_enrolment_live
    on partner_insurance_enrolments (account_id, policy_id) where status <> 'EXITED';
create index idx_partner_enrolments_status on partner_insurance_enrolments (status, created_at);
