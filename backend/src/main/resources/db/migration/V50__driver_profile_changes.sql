-- A verified partner's change to what riders identify her by - her name,
-- date of birth, photo, vehicle or its registration number - waiting for an
-- operator. Until it is approved, riders keep seeing what was checked.
--
-- Only the fields she changed are filled in. rc_document_key is the photo
-- of the new registration certificate, required before a vehicle change can
-- be approved. One pending change per partner at a time.
create table driver_profile_changes (
    id                          uuid primary key,
    account_id                  uuid         not null,
    status                      varchar(20)  not null,
    name                        varchar(150),
    date_of_birth               date,
    vehicle_type                varchar(20),
    vehicle_registration_number varchar(20),
    photo_key                   varchar(512),
    rc_document_key             varchar(512),
    requested_at                timestamptz  not null,
    decided_at                  timestamptz,
    decided_by                  uuid,
    decision_note               varchar(500),
    created_at                  timestamptz  not null,
    updated_at                  timestamptz  not null
);

create unique index uq_driver_profile_changes_one_pending
    on driver_profile_changes (account_id) where status = 'PENDING';
create index idx_driver_profile_changes_status on driver_profile_changes (status, requested_at);
create index idx_driver_profile_changes_account on driver_profile_changes (account_id, requested_at desc);
