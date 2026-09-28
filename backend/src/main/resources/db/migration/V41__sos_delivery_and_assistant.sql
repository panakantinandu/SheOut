-- 1. How an SOS reached SheOut, and what set it off.
--
-- An SOS can now be raised by a discreet gesture (shake / back-tap) as well
-- as the button, and when the data connection is bad the app does three
-- things at once: keeps trying the server, opens her phone's own SMS app to
-- her contacts, and - with no signal at all - keeps the alert on the phone
-- and sends it the moment a connection returns. Each alert records which of
-- those happened, so an operator can see it beside the contacts reached.
alter table sos_alert
    -- BUTTON, SHAKE, BACK_TAP or SHORTCUT (an iOS Back Tap / Android Quick Tap shortcut opening the SOS link).
    add column trigger_source      varchar(20) not null default 'BUTTON',
    -- DATA: reached SheOut while she was raising it. DELAYED_QUEUE: held on
    -- her phone with no signal and sent when the connection came back.
    add column delivery_channel    varchar(20) not null default 'DATA',
    -- Her phone's SMS app was opened with her contacts and location filled
    -- in (the SMS fallback). SheOut cannot see whether she pressed send.
    add column sms_fallback_opened boolean     not null default false,
    -- When she raised it on the phone - earlier than created_at when it waited in the queue.
    add column triggered_at        timestamptz,
    -- The phone's own id for this alert: a retry after a lost response, or
    -- the queue sending it again, is the same alert and never texts twice.
    add column client_alert_id     uuid unique;

-- 2. The help assistant's use, per account per day - the daily cap, and what it costs.
create table assistant_usage (
    account_id          uuid        not null,
    day                 date        not null,
    messages            int         not null default 0,
    input_tokens        bigint      not null default 0,
    output_tokens       bigint      not null default 0,
    cache_read_tokens   bigint      not null default 0,
    cache_write_tokens  bigint      not null default 0,
    escalations         int         not null default 0,
    emergencies         int         not null default 0,
    primary key (account_id, day)
);

create index idx_assistant_usage_day on assistant_usage (day);
