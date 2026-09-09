-- Debugging/audit trail for every notification delivery attempt (Part A) -
-- nothing else reads this back to decide behavior, contrast payments.
create table notification_log (
    id                   uuid primary key,
    recipient_account_id uuid            not null,
    recipient_address    varchar(255),
    type                 varchar(30)     not null,
    channel              varchar(20)     not null,
    status               varchar(20)     not null,
    failure_reason       varchar(500),
    created_at           timestamp       not null,
    updated_at           timestamp       not null
);
create index idx_notification_log_recipient on notification_log (recipient_account_id);

-- SOS alerts (Part B). booking_id is nullable - a customer can trigger SOS
-- with no active booking at all. status is ACTIVE for every row today -
-- nothing transitions it to RESOLVED yet (no admin module to do it from),
-- see SosStatus's Javadoc.
create table sos_alert (
    id                   uuid primary key,
    customer_account_id  uuid              not null,
    booking_id           uuid,
    lat                  double precision  not null,
    lng                  double precision  not null,
    status               varchar(20)       not null,
    contacts_notified    integer           not null,
    contacts_failed      integer           not null,
    created_at           timestamp         not null,
    updated_at           timestamp         not null
);
create index idx_sos_alert_status on sos_alert (status);
