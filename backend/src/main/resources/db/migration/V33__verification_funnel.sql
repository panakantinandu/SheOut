-- Who reached the upload screen, and who actually sent something.
--
-- Nobody knows whether identity verification is where new riders give up,
-- because nothing has ever been recorded between "opened the app" and
-- "submitted a document". This is the smallest thing that answers it later:
-- a row when she reaches the upload screen, a row when she picks a file, and
-- the submission itself already exists on the verification record. The gap
-- between those counts is the drop-off.
--
-- Deliberately not a general analytics table: one question, one step name,
-- no properties to sprawl into. And nothing about the document itself.
create table verification_funnel_events (
    id          uuid        primary key,
    account_id  uuid        not null references accounts (id),
    role        varchar(20) not null,
    -- UPLOAD_VIEWED or DOCUMENT_CHOSEN - see VerificationFunnelStep.
    step        varchar(40) not null,
    occurred_at timestamptz not null,
    created_at  timestamptz not null,
    updated_at  timestamptz not null
);

-- "Did this account ever reach that step" - the common read.
create unique index idx_verification_funnel_account_step
    on verification_funnel_events (account_id, step);

create index idx_verification_funnel_occurred on verification_funnel_events (occurred_at desc);
