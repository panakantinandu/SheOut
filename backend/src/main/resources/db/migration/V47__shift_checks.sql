-- A partner's start-of-shift safety check: a live selfie, matched on her
-- phone against the verified selfie on file, and for a two-wheeler a photo
-- with her helmet on. One row per attempt, from the moment the prompts are
-- issued; see ShiftCheckService for the statuses.
create table shift_checks (
    id                   uuid primary key,
    account_id           uuid         not null,
    challenge_id         varchar(64)  not null unique,
    prompts              varchar(100) not null,
    challenge_expires_at timestamptz  not null,
    status               varchar(20)  not null,
    selfie_key           varchar(512),
    frames_key           varchar(512),
    helmet_key           varchar(512),
    face_result          varchar(20),
    face_distance        double precision,
    submitted_at         timestamptz,
    reviewed_at          timestamptz,
    reviewed_by          uuid,
    review_note          varchar(500),
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null
);

create index idx_shift_checks_account_created on shift_checks (account_id, created_at desc);
create index idx_shift_checks_status on shift_checks (status);
