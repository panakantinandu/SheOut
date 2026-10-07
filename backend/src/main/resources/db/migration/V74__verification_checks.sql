-- The checks a verification is made of: identity, selfie match, licence,
-- vehicle, insurance, PAN and background. One row per (account, check).
--
-- status is PENDING, PASSED, NEEDS_REVIEW, FAILED_AUTO or
-- APPROVED_BY_PERSON. There is no rejected status: only a person turns
-- somebody down, and that is recorded on the document or ID decision and in
-- the audit trail, with the check going back to PENDING.
--
-- subject_id / subject_version say what the status is about: the document
-- row (partner_documents.id) or, for her ID and selfie, when they were
-- sent. When the subject changes the check is worked out again from the
-- start, and a person's earlier approval no longer applies to it.
--
-- findings_json is the list of things the automatic checks noticed, as
-- [{"code":"NAME_MISMATCH","detail":"..."}], so the reviewer sees each one
-- and must acknowledge each before approving.
--
-- override_*: a FAILED_AUTO check accepted anyway, which needs a written
-- note from one person and a second person's sign-off (staff approvals).
create table verification_checks (
    id                     uuid primary key,
    account_id             uuid          not null,
    check_type             varchar(30)   not null,
    required               boolean       not null,
    status                 varchar(20)   not null,
    reason                 varchar(300),
    findings_json          text,
    provider               varchar(40),
    -- What the provider said about the current subject, kept so it is asked
    -- once per subject rather than on every read.
    provider_status        varchar(20),
    provider_reason        varchar(300),
    provider_reference     varchar(200),
    subject_id             uuid,
    subject_version        varchar(64),
    decided_by             uuid,
    decided_by_role        varchar(20)   not null,
    decided_at             timestamptz   not null,
    override_note          varchar(1000),
    override_requested_by  uuid,
    override_approved_by   uuid,
    created_at             timestamptz   not null,
    updated_at             timestamptz   not null
);

create unique index uq_verification_checks_account_type on verification_checks (account_id, check_type);
create index idx_verification_checks_status on verification_checks (status, decided_at);
create index idx_verification_checks_decided on verification_checks (decided_at);
