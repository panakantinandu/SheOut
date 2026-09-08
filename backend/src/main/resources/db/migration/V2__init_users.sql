create table customer_profiles (
    id            uuid primary key,
    account_id    uuid         not null unique,
    name          varchar(150),
    home_address  varchar(500),
    work_address  varchar(500),
    verified      boolean      not null default false,
    created_at    timestamp    not null,
    updated_at    timestamp    not null
);

create table driver_profiles (
    id                          uuid primary key,
    account_id                  uuid         not null unique,
    name                        varchar(150),
    vehicle_type                varchar(20),
    vehicle_registration_number varchar(20),
    online_status               varchar(20)  not null default 'OFFLINE',
    verified                    boolean      not null default false,
    created_at                  timestamp    not null,
    updated_at                  timestamp    not null
);

create table emergency_contacts (
    id                   uuid primary key,
    customer_profile_id  uuid         not null references customer_profiles (id),
    name                 varchar(150) not null,
    phone_number         varchar(20)  not null,
    relationship         varchar(50)  not null,
    created_at           timestamp    not null,
    updated_at           timestamp    not null
);

create index idx_emergency_contacts_customer_profile on emergency_contacts (customer_profile_id);
