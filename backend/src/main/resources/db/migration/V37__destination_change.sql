-- A rider asks, mid-trip, to be taken somewhere else; her partner says yes
-- or no. See DestinationChangeService.
--
-- One row per request, kept whatever the answer: what was asked, what it
-- would cost against what it cost before, and what the partner said. The
-- booking itself changes only when she says yes.
create table booking_destination_changes (
    id                  uuid primary key,
    booking_id          uuid           not null references bookings (id),
    requested_by        uuid           not null,
    status              varchar(20)    not null,

    old_drop_label      varchar(255)   not null,
    old_drop_lat        double precision not null,
    old_drop_lng        double precision not null,
    new_drop_label      varchar(255)   not null,
    new_drop_lat        double precision not null,
    new_drop_lng        double precision not null,

    old_fare            numeric(10, 2) not null,
    new_fare            numeric(10, 2) not null,
    old_distance_km     numeric(8, 2),
    new_distance_km     numeric(8, 2)  not null,
    new_distance_routed boolean        not null,

    expires_at          timestamptz    not null,
    answered_at         timestamptz,

    created_at          timestamptz    not null,
    updated_at          timestamptz    not null
);

create index idx_destination_changes_booking on booking_destination_changes (booking_id, created_at desc);

-- One question at a time. A request past its expiry is still PENDING in the
-- row until something touches it (see DestinationChangeEntity.effectiveStatus),
-- and the service settles it before a new one is written.
create unique index uq_destination_changes_one_pending on booking_destination_changes (booking_id)
    where status = 'PENDING';

-- On the booking: when its drop was changed by agreement, and the distance
-- it was first quoted on. The route check then measures against the agreed
-- route, and an operator reading a flagged trip can see that it changed.
alter table bookings
    add column destination_changed_at timestamptz,
    add column original_quoted_distance_km numeric(8, 2);
