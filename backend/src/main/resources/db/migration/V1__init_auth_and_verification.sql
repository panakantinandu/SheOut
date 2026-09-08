create table accounts (
    id           uuid primary key,
    phone_number varchar(20)  not null unique,
    role         varchar(20)  not null,
    created_at   timestamp    not null,
    updated_at   timestamp    not null
);

create table verification_records (
    id                          uuid primary key,
    account_id                  uuid         not null unique,
    role                        varchar(20)  not null,
    gender_verification_status  varchar(20)  not null,
    police_verification_status  varchar(20),
    aadhaar_document_key        varchar(500),
    reviewed_by                 varchar(100),
    reviewed_at                 timestamp,
    rejection_reason            varchar(1000),
    created_at                  timestamp    not null,
    updated_at                  timestamp    not null
);

create index idx_verification_records_gender_status on verification_records (gender_verification_status);
