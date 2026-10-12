-- Proof of delivery for parcels: who it is for, what the sender agreed to,
-- the delivery code, the photos, the drop check, the receiver-absent flow,
-- and a dispute - see docs/parcel-dispatch-audit.md (Part 1).
--
-- One row per delivery booked after this change. A delivery with no row
-- here was booked before it and finishes under the old rules, so nothing
-- under way at deploy time is stranded.
create table parcel_deliveries (
    booking_id              uuid primary key,
    -- Who it is for. The phone is kept only for the receiver SMS (off until
    -- DLT is approved) and for support, and is erased with the photos.
    receiver_name           varchar(80)  not null,
    receiver_phone          varchar(16),
    receiver_phone_erased_at timestamptz,
    -- Records only: no compensation is promised against it.
    declared_value          numeric(10, 2),
    -- What the sender ticked: the prohibited-items list (by version) and
    -- "under the weight limit, fits a bag".
    prohibited_ack_version  varchar(20)  not null,
    size_ack                boolean      not null,
    acknowledged_at         timestamptz  not null,
    -- The code the receiver gives the partner at the drop. The sender sees
    -- it; the partner never does.
    delivery_code           varchar(4),
    delivery_code_attempts  integer      not null default 0,
    delivery_verified_at    timestamptz,
    -- The drop check at "Delivered": AT_DROP, OUTSIDE or UNAVAILABLE.
    drop_check              varchar(20),
    drop_check_distance_m   integer,
    outside_reason          varchar(40),
    outside_note            varchar(500),
    review_flagged_at       timestamptz,
    review_flag_reason      varchar(40),
    reviewed_at             timestamptz,
    reviewed_by             uuid,
    review_note             varchar(1000),
    -- Receiver absent or refusing: what the partner said, and what the
    -- sender chose (WAIT, LEAVE_WITH, RETURN).
    exception_reason        varchar(40),
    exception_note          varchar(500),
    exception_at            timestamptz,
    sender_choice           varchar(20),
    sender_choice_at        timestamptz,
    leave_with_name         varchar(80),
    return_fare             numeric(10, 2),
    return_fare_waived_at   timestamptz,
    return_fare_waived_by   uuid,
    -- A dispute keeps the photos past retention and the partner's earnings held.
    dispute_opened_at       timestamptz,
    dispute_opened_by       uuid,
    dispute_note            varchar(1000),
    dispute_closed_at       timestamptz,
    dispute_closed_by       uuid,
    dispute_outcome         varchar(30),
    dispute_resolution_note varchar(1000),
    created_at              timestamptz  not null default now(),
    updated_at              timestamptz  not null default now(),
    constraint chk_parcel_declared_value check (declared_value is null or declared_value >= 0),
    constraint chk_parcel_drop_check check (drop_check is null or drop_check in ('AT_DROP', 'OUTSIDE', 'UNAVAILABLE')),
    constraint chk_parcel_sender_choice check (sender_choice is null or sender_choice in ('WAIT', 'LEAVE_WITH', 'RETURN'))
);
create index idx_parcel_deliveries_review on parcel_deliveries (review_flagged_at) where review_flagged_at is not null and reviewed_at is null;
create index idx_parcel_deliveries_dispute on parcel_deliveries (dispute_opened_at) where dispute_opened_at is not null and dispute_closed_at is null;

-- The photos: taken in the partner's camera, stamped with the server's own
-- time and her last trusted position at upload. Private (document storage,
-- short signed links); the file goes after the retention period and the
-- row stays as the record that it existed.
create table parcel_photos (
    id            uuid primary key,
    booking_id    uuid         not null,
    kind          varchar(20)  not null,
    storage_key   varchar(500),
    uploaded_by   uuid         not null,
    taken_at      timestamptz  not null,
    lat           double precision,
    lng           double precision,
    content_hash  varchar(64)  not null,
    deleted_at    timestamptz,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),
    constraint chk_parcel_photo_kind check (kind in ('PICKUP', 'DELIVERY', 'EXCEPTION'))
);
create index idx_parcel_photos_booking on parcel_photos (booking_id);
create index idx_parcel_photos_live on parcel_photos (taken_at) where deleted_at is null;

-- A partner's earnings from a parcel, held until the dispute window has
-- passed with no complaint. An operator can hold it longer, release it, or
-- withhold it when the partner was at fault - always with a reason.
create table earning_holds (
    booking_id        uuid primary key,
    driver_account_id uuid           not null,
    amount            numeric(12, 2) not null,
    release_at        timestamptz    not null,
    status            varchar(20)    not null,
    operator_hold     boolean        not null default false,
    reason            varchar(500),
    decided_by        uuid,
    decided_at        timestamptz,
    created_at        timestamptz    not null default now(),
    updated_at        timestamptz    not null default now(),
    constraint chk_earning_hold_status check (status in ('HELD', 'RELEASED', 'WITHHELD'))
);
create index idx_earning_holds_due on earning_holds (release_at) where status = 'HELD' and operator_hold = false;

-- What is held, as a figure on the wallet: available = earned - cash - paid
-- out - pending payouts - held.
alter table driver_wallets add column held numeric(12, 2) not null default 0;
