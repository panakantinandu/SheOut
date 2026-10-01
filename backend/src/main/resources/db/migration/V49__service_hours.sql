-- When SheOut takes new bookings. One row: the operating mode, the daily
-- window (India time), and an operator's pause - a stop on new bookings
-- right now, for a reason riders are shown, until a time or until resumed.
--
-- Seeded ALWAYS_OPEN, which is what SheOut has done until now, so this
-- deploy changes nothing on its own. The 06:00-22:00 window is filled in so
-- turning the schedule on in the console is one switch, not a form.
create table service_hours (
    id            uuid primary key,
    mode          varchar(20)  not null,
    opens_at      time         not null,
    closes_at     time         not null,
    paused        boolean      not null default false,
    pause_reason  varchar(300),
    paused_until  timestamptz,
    paused_at     timestamptz,
    paused_by     uuid,
    updated_by    uuid,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null
);

insert into service_hours (id, mode, opens_at, closes_at, paused, created_at, updated_at)
values (gen_random_uuid(), 'ALWAYS_OPEN', '06:00', '22:00', false, now(), now());

-- Who changed the hours, and to what. Read in the console beside the
-- controls: "who paused bookings at 9 pm" should have an answer.
create table service_hours_changes (
    id          uuid primary key,
    changed_by  uuid         not null,
    summary     varchar(500) not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);

create index idx_service_hours_changes_created on service_hours_changes (created_at desc);
