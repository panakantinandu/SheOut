-- Why a person sent her ID or a document back, as one of a fixed list
-- (RejectionReason), so the apps can tell her how to fix it in her own
-- language. rejection_reason keeps the English message, for older apps.
-- Null on rows decided before the list, and on anything not rejected.
alter table verification_records add column rejection_code varchar(40);
alter table partner_documents add column rejection_code varchar(40);

-- The weekly 10% second look: one row per account a second reviewer looked
-- at, for the ISO week it was sampled in (2026-W41). outcome is AGREE, or
-- CONCERN with a note saying what the first review missed.
create table verification_sample_reviews (
    id           uuid primary key,
    account_id   uuid          not null,
    sample_week  varchar(8)    not null,
    reviewed_by  uuid          not null,
    outcome      varchar(10)   not null,
    note         varchar(1000),
    created_at   timestamptz   not null,
    updated_at   timestamptz   not null
);

create unique index uq_verification_sample_reviews on verification_sample_reviews (account_id, sample_week);

-- Reviewer override rates read the audit trail by who acted and when.
create index idx_verification_audit_actor on verification_audit_events (actor_role, at);
