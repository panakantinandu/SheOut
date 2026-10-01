-- Trip watch: something about a live trip that a person should look at -
-- running far over time, the partner's phone gone quiet, a long stop away
-- from the drop, a trip accepted and never started, an impossible jump in
-- position. Raised by TripWatch, cleared by itself when the condition ends
-- or by an operator who has checked. Nothing here ever cancels a trip.
create table trip_alerts (
    id           uuid primary key,
    booking_id   uuid         not null,
    kind         varchar(30)  not null,
    detail       varchar(300),
    raised_at    timestamptz  not null,
    resolved_at  timestamptz,
    resolved_by  uuid,
    note         varchar(500),
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null
);

-- One open alert of each kind per trip.
create unique index uq_trip_alerts_open on trip_alerts (booking_id, kind) where resolved_at is null;
create index idx_trip_alerts_open on trip_alerts (raised_at) where resolved_at is null;
create index idx_trip_alerts_booking on trip_alerts (booking_id);
