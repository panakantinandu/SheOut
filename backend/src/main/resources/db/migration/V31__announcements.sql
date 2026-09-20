-- What was broadcast, to whom, and what actually went out.
--
-- The counts are recorded rather than recomputed: an announcement is a thing
-- that happened at a moment, and "how many emails went out that night" cannot
-- be answered later from a list of accounts that has changed since.
--
-- push_accepted is deliberately not a count of phones. A topic broadcast is
-- one call to FCM, which fans it out and never says how many devices it
-- reached; a number here would be invented. It records whether FCM took the
-- message for each audience.
create table announcements (
    id              uuid primary key,
    title           varchar(120) not null,
    body            varchar(1000) not null,
    audience        varchar(20)  not null,
    sent_by         uuid         not null references accounts (id),
    created_at      timestamptz  not null,
    push_accepted   int          not null default 0,
    push_failed     int          not null default 0,
    emails_sent     int          not null default 0,
    emails_failed   int          not null default 0,
    sms_sent        int          not null default 0,
    sms_failed      int          not null default 0,
    -- Off unless an operator ticked it for something genuinely urgent: every
    -- SMS must match a DLT-approved template and costs money per message.
    sms_requested   boolean      not null default false,
    -- Anything an operator needs to know afterwards: a channel that was not
    -- configured, a provider that refused.
    note            varchar(500)
);

create index idx_announcements_created on announcements (created_at desc);
