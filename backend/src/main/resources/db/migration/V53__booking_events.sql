-- Every change to a trip's state, with who made it and from where: the
-- answer to "who cancelled this, when, from which phone" without reading
-- logs. Written beside each transition; never updated, never deleted.
-- actor_id is null when the system did it (the search running out).
create table booking_events (
    id           uuid primary key,
    booking_id   uuid         not null,
    event        varchar(30)  not null,
    from_status  varchar(30),
    to_status    varchar(30),
    actor_id     uuid,
    actor_role   varchar(20),
    detail       varchar(300),
    request_id   varchar(64),
    device       varchar(120),
    at           timestamptz  not null,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null
);

create index idx_booking_events_booking on booking_events (booking_id, at);
