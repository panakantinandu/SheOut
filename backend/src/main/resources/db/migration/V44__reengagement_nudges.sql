-- "We miss you" reminders (see ReengagementNudger): the one a rider or
-- partner was last sent for her current stretch away from the app, so no
-- step of the ladder is sent twice and none comes sooner than a week after
-- the last. One row per account, rewritten each time; reset when she comes
-- back, which is read from the push device and session activity, not here.
create table reengagement_nudges (
    account_id          uuid        primary key,
    -- 1, 2 or 3: the reminder sent at about a week, two weeks and a month away.
    stage               integer     not null,
    last_nudged_at      timestamptz not null,
    -- When she was last seen as of that reminder: a later sighting means she
    -- came back, and the ladder starts again from the beginning.
    seen_at_when_nudged timestamptz not null
);
