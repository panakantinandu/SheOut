-- A partner's documents: driving licence, the vehicle's RC, its insurance,
-- PUC and fitness certificates, and the police certificate or background
-- report behind her police check. One row per document sent in.
--
-- One CURRENT row per (account, type): the row with superseded_at null is
-- the one that counts. A new upload supersedes the row before it rather
-- than overwriting it, so what was checked, by whom and when stays on file.
-- replaces_document_id points at the row a renewal is waiting to replace:
-- while it is under review the earlier, still-valid one keeps her working
-- (see PartnerDocumentService.readiness).
--
-- valid_until is null for types that do not expire on a printed date (the
-- police certificate is re-verified on a period instead, and a background
-- report is evidence, not a licence).
--
-- metadata_json holds type-specific facts - for VEHICLE_INSURANCE, whether
-- the policy is for commercial or private use - so a new fact about one
-- document type does not need a column on every row.
create table partner_documents (
    id                   uuid primary key,
    account_id           uuid          not null,
    type                 varchar(40)   not null,
    document_number      varchar(64),
    issued_on            date,
    valid_until          date,
    document_key         varchar(500),
    status               varchar(20)   not null,
    rejection_reason     varchar(1000),
    reviewed_by          uuid,
    reviewed_at          timestamptz,
    source               varchar(20)   not null,
    provider_reference   varchar(200),
    metadata_json        text,
    submitted_at         timestamptz,
    expired_at           timestamptz,
    -- The smallest "days before expiry" reminder already sent (30, 7, 1),
    -- so each reminder goes once however often the sweep runs.
    last_reminder_days   integer,
    replaces_document_id uuid,
    superseded_at        timestamptz,
    created_at           timestamptz   not null,
    updated_at           timestamptz   not null
);

create unique index uq_partner_documents_current
    on partner_documents (account_id, type) where superseded_at is null;
create index idx_partner_documents_account on partner_documents (account_id, type, created_at desc);
create index idx_partner_documents_review on partner_documents (status, submitted_at) where superseded_at is null;
create index idx_partner_documents_valid_until
    on partner_documents (valid_until) where superseded_at is null and valid_until is not null;

-- The RC photo every partner already sent with her ID. It was looked at
-- beside the ID, but nobody ever recorded its number or how long it is
-- valid for, so it arrives here UNDER_REVIEW: an operator reads the date
-- off the photo and approves it again. The storage object is shared, not
-- copied; verification_records.rc_document_key is left in place for the
-- release that is still running while this one starts, and is dropped in a
-- later release (see the README).
insert into partner_documents (id, account_id, type, document_key, status, source, metadata_json,
                               submitted_at, created_at, updated_at)
select gen_random_uuid(), account_id, 'VEHICLE_RC', rc_document_key, 'UNDER_REVIEW', 'PARTNER_UPLOAD',
       '{"migratedFrom":"verification_records.rc_document_key"}',
       coalesce(document_submitted_at, updated_at), now(), now()
from verification_records
where rc_document_key is not null;

-- Who did what to a partner's verification, and when: every upload, every
-- decision, every expiry, and every time an operator opened one of her
-- documents. Append-only. actor_id is null for the system (the expiry
-- sweep) and for a provider callback.
create table verification_audit_events (
    id            uuid primary key,
    account_id    uuid         not null,
    actor_id      uuid,
    actor_role    varchar(20)  not null,
    action        varchar(40)  not null,
    document_id   uuid,
    document_type varchar(40),
    detail        varchar(1000),
    at            timestamptz  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null
);

create index idx_verification_audit_account on verification_audit_events (account_id, at desc);
