-- Police verification with evidence.
--
-- A new table rather than more columns on verification_records, because a
-- police check is not a one-off fact about a partner: it is redone every
-- POLICE_REVERIFY_MONTHS, and each decision has its own evidence. Columns
-- would be overwritten by the next check, losing what the previous one
-- rested on - and "was she police-verified, and on what, on the night of
-- trip X" is exactly the question a complaint or an audit asks. One row per
-- decision; verification_records keeps only the live state the gate reads.
create table police_verifications (
    id                          uuid primary key,
    account_id                  uuid          not null,
    outcome                     varchar(20)   not null,
    -- TS_POLICE_PVC (Telangana Police i-Verify certificate),
    -- OTHER_STATE_POLICE, THIRD_PARTY_BGV. Null on a rejection.
    police_method               varchar(30),
    police_certificate_number   varchar(64),
    police_issued_on            date,
    police_issuing_authority    varchar(200),
    police_reverify_due_on      date,
    -- The certificate (or, when allowed alone, the background report)
    -- the decision rests on, and a background report attached as extra
    -- evidence beside a police certificate.
    police_document_id          uuid references partner_documents (id),
    extra_document_id           uuid references partner_documents (id),
    -- The consent she had given when the check was recorded.
    police_consent_at           timestamptz,
    police_consent_text_version varchar(40),
    rejection_reason            varchar(1000),
    decided_by                  uuid          not null,
    decided_at                  timestamptz   not null,
    created_at                  timestamptz   not null,
    updated_at                  timestamptz   not null
);

create index idx_police_verifications_account on police_verifications (account_id, decided_at desc);

-- The live state, read by the gate and the sweep.
alter table verification_records add column consent_version varchar(40);
alter table verification_records add column consent_accepted_at timestamptz;
alter table verification_records add column police_reverify_due_on date;
alter table verification_records add column police_reverify_reminder_days integer;

-- Every police check recorded before this was a button with no evidence
-- behind it. Rather than switching those partners off here, their check is
-- made due today: the next expiry sweep puts it back to PENDING through the
-- ordinary path, which publishes VerificationLapsed (users clears its cached
-- flag and takes her offline after any trip she is on) and tells her, in her
-- language, to send a police certificate. An operator then records it with
-- evidence like any other.
update verification_records
set police_reverify_due_on = current_date
where police_verification_status = 'VERIFIED';
