create table bookings (
    id             uuid primary key,
    type           varchar(20)     not null,
    category       varchar(20)     not null,
    status         varchar(20)     not null,
    customer_id    uuid            not null,
    driver_id      uuid,

    pickup_label   varchar(255)    not null,
    pickup_lat     double precision not null,
    pickup_lng     double precision not null,

    drop_label     varchar(255)    not null,
    drop_lat       double precision not null,
    drop_lng       double precision not null,

    fare_estimate  numeric(10, 2)  not null,
    final_fare     numeric(10, 2),

    matched_at     timestamp,
    accepted_at    timestamp,
    started_at     timestamp,
    completed_at   timestamp,
    cancelled_at   timestamp,

    created_at     timestamp       not null,
    updated_at     timestamp       not null
);

create index idx_bookings_customer on bookings (customer_id);
create index idx_bookings_driver on bookings (driver_id);
create index idx_bookings_status on bookings (status);
