-- notification_log becomes the notification itself: one row per thing SheOut
-- told an account, carrying what it said - which is what an inbox shows.
-- Until now it was one row per SMS attempt holding only a type and a
-- delivery status, so the Notifications screen could list nothing but
-- "Booking requested - SMS - Failed". How each copy of a notification was
-- delivered (every push device, an email, an SMS, each SOS contact) moves to
-- notification_deliveries, one row per attempt, as before.

alter table notification_log add column title   varchar(120);
alter table notification_log add column body    varchar(500);
alter table notification_log add column link    varchar(200);
alter table notification_log add column read_at timestamp;

create table notification_deliveries (
    id                uuid primary key,
    notification_id   uuid         not null references notification_log (id) on delete cascade,
    channel           varchar(20)  not null,
    -- Phone number, email address or push device token. Null once erased
    -- by account deletion, or when there was no address to try.
    recipient_address varchar(512),
    status            varchar(20)  not null,
    failure_reason    varchar(500),
    created_at        timestamp    not null,
    updated_at        timestamp    not null
);
create index idx_notification_deliveries_notification on notification_deliveries (notification_id);

-- Every existing row was exactly one delivery attempt of its own notification.
insert into notification_deliveries (id, notification_id, channel, recipient_address, status, failure_reason, created_at, updated_at)
select gen_random_uuid(), id, channel, recipient_address, status, failure_reason, created_at, updated_at
from notification_log;

-- What the old rows would have said. The message text was never stored, so
-- the history gets a title and no body rather than invented copy.
update notification_log set title = case type
    when 'BOOKING_REQUESTED' then 'Booking requested'
    when 'BOOKING_ACCEPTED'  then 'Your partner accepted your booking'
    when 'BOOKING_COMPLETED' then 'Trip completed'
    when 'BOOKING_CANCELLED' then 'Booking cancelled'
    when 'ACCOUNT_VERIFIED'  then 'Your account is verified'
    when 'SOS_ALERT'         then 'SOS alert sent to an emergency contact'
    when 'SUPPORT_REPLY'     then 'Support replied to your ticket'
    else 'SheOut'
end;
-- History is not news: nothing sent before the inbox existed shows as unread.
update notification_log set read_at = created_at;

alter table notification_log alter column title set not null;
alter table notification_log drop column recipient_address;
alter table notification_log drop column channel;
alter table notification_log drop column status;
alter table notification_log drop column failure_reason;

drop index if exists idx_notification_log_recipient;
create index idx_notification_log_recipient_created on notification_log (recipient_account_id, created_at desc);
create index idx_notification_log_unread on notification_log (recipient_account_id) where read_at is null;

-- One row per device an account has turned push on for. An account can have
-- several (her phone, a tablet, a desktop browser); a token belongs to one
-- account at a time - the same browser signing in as someone else takes the
-- token over. account_role is kept so "every operator's device" is one query
-- for SOS alerts, without notifications asking auth for a list of admins.
create table push_devices (
    id           uuid primary key,
    account_id   uuid          not null,
    account_role varchar(20)   not null,
    token        varchar(512)  not null unique,
    user_agent   varchar(300),
    last_seen_at timestamp     not null,
    created_at   timestamp     not null,
    updated_at   timestamp     not null
);
create index idx_push_devices_account on push_devices (account_id);
create index idx_push_devices_role on push_devices (account_role);
