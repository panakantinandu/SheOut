-- Manual court-record screening of partners, from public court records
-- (eCourts), done by a person and written down. A screening aid, NOT police
-- verification: nothing here ever tells anyone a partner is "police
-- verified". Off unless ADMIN_COURT_SCREEN_ENABLED=true. See
-- driververification.internal.CourtScreeningService.
--
-- Minimal by design. A match keeps the case type, its status, the year and
-- the court - never the judgment, and never anyone else's name. A rejection
-- keeps a reason category, not the case. Everything identifying is
-- anonymised by the retention tasks (court-screenings-rejected,
-- court-screenings-departed).
create table court_screenings (
    id                    uuid primary key,
    account_id            uuid          not null,
    status                varchar(30)   not null,
    requested_by          uuid          not null,
    -- The partner's own consent to this search: the wording's version and when.
    consent_version       varchar(40),
    consent_at            timestamptz,
    -- What the reviewer searched under: spellings and transliterations of her
    -- name, the father's or husband's name as printed on her ID, the districts.
    name_variants         varchar(1000),
    relative_name         varchar(150),
    districts             varchar(1000),
    -- The reviewer who logged the first possible or confirmed match. She may
    -- not sign it off, nor decide the case.
    flagged_by            uuid,
    second_reviewer       uuid,
    second_outcome        varchar(20),
    second_note           varchar(1000),
    second_at             timestamptz,
    -- The partner is told and may answer before any rejection.
    partner_notified_at   timestamptz,
    response_due_at       timestamptz,
    partner_response      varchar(1000),
    partner_responded_at  timestamptz,
    decision              varchar(30),
    reason_category       varchar(40),
    decision_note         varchar(1000),
    decided_by            uuid,
    decided_at            timestamptz,
    -- A police clearance certificate she sent, if one was asked for.
    pcc_issued_on         date,
    pcc_authority         varchar(200),
    pcc_valid_until       date,
    pcc_document_id       uuid,
    left_at               timestamptz,
    anonymised_at         timestamptz,
    created_at            timestamptz   not null,
    updated_at            timestamptz   not null
);

-- One open screening per partner at a time; decided ones stay as history.
create unique index uq_court_screenings_open on court_screenings (account_id) where decided_at is null and status <> 'WITHDRAWN';
create index idx_court_screenings_status on court_screenings (status, updated_at);

create table court_screening_searches (
    id              uuid primary key,
    screening_id    uuid          not null references court_screenings (id),
    district        varchar(100)  not null,
    court           varchar(150),
    name_variant    varchar(150)  not null,
    searched_on     date          not null,
    reviewer_id     uuid          not null,
    outcome         varchar(20)   not null,
    note            varchar(1000),
    case_type       varchar(100),
    case_status     varchar(30),
    case_year       integer,
    screenshot_key  varchar(512),
    created_at      timestamptz   not null,
    updated_at      timestamptz   not null
);

create index idx_court_screening_searches_screening on court_screening_searches (screening_id, created_at);

-- For the console's report and for anyone with database access: counts,
-- never names.
create view court_screening_report as
select
    status,
    decision,
    count(*)                                                        as screenings,
    count(*) filter (where flagged_by is not null)                  as flagged,
    count(*) filter (where flagged_by is not null and second_at is null and decided_at is null) as awaiting_second_signoff,
    count(*) filter (where status = 'AWAITING_RESPONSE' and response_due_at < now()) as response_overdue,
    avg(extract(epoch from (decided_at - consent_at)) / 3600)
        filter (where decided_at is not null and consent_at is not null) as avg_hours_consent_to_decision
from court_screenings
group by status, decision;
