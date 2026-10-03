-- Phase 2 of the console's security: an audit log nobody can quietly edit,
-- re-entering the authenticator before sensitive actions, and agents seeing
-- only the work they have taken.

-- Every staff action worth answering for, reads of sensitive data included:
-- who, what, which record, when, from where, why (for a reveal), and how it
-- ended. Each row carries the hash of the row before it, so editing or
-- deleting any row breaks the chain from that point on, and the daily check
-- (StaffAuditChainCheck) says where.
create table staff_audit_events (
    seq          bigint generated always as identity primary key,
    id           uuid          not null unique,
    occurred_at  timestamptz   not null,
    -- Null for the system itself (the first-owner invitation, a scheduled check).
    staff_id     uuid,
    account_id   uuid,
    staff_role   varchar(30),
    -- e.g. pii.phone.reveal, staff.role.change, POST /api/v1/admin/accounts/{accountId}/block
    action       varchar(120)  not null,
    permission   varchar(60),
    -- OK, DENIED or FAILED.
    result       varchar(10)   not null,
    target_type  varchar(40),
    target_id    varchar(80),
    -- The reason typed for a reveal, a block, a disable.
    reason       varchar(500),
    -- Before/after for role and configuration changes, as JSON. Never a
    -- secret: no password, code, token or document content is ever put here.
    detail       varchar(2000),
    ip_address   varchar(64),
    user_agent   varchar(200),
    session_id   uuid,
    request_id   varchar(64),
    prev_hash    varchar(64)   not null,
    hash         varchar(64)   not null unique,
    constraint chk_staff_audit_result check (result in ('OK', 'DENIED', 'FAILED'))
);

create index idx_staff_audit_time on staff_audit_events (occurred_at desc);
create index idx_staff_audit_staff on staff_audit_events (staff_id, occurred_at desc);
create index idx_staff_audit_action on staff_audit_events (action, occurred_at desc);

-- Append-only, enforced by the database rather than trusted to the code.
-- The application connects as the table's owner on Render (a separate,
-- unprivileged role is not available on that plan), and an owner can drop a
-- trigger - so this stops mistakes and casual edits, and the hash chain is
-- what catches a deliberate one. See README "Staff accounts and roles".
create function staff_audit_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'staff_audit_events is append-only';
end;
$$;

create trigger trg_staff_audit_no_update before update or delete on staff_audit_events
    for each row execute function staff_audit_append_only();
create trigger trg_staff_audit_no_truncate before truncate on staff_audit_events
    for each statement execute function staff_audit_append_only();

-- When she last re-entered her authenticator code on this session. Sensitive
-- actions need it to be within the last five minutes.
alter table staff_sessions add column step_up_at timestamptz;

-- Browsers each member of staff has signed in from, so a new one can be told
-- to her and to every owner. Only a hash of the browser's description.
create table staff_known_devices (
    staff_id       uuid        not null references staff_members (id) on delete cascade,
    device_hash    varchar(64) not null,
    first_seen_at  timestamptz not null,
    last_seen_at   timestamptz not null,
    primary key (staff_id, device_hash)
);

-- Who has taken a piece of work. One row per item while someone has it; an
-- agent sees the items she holds and the ones nobody holds.
create table staff_work_assignments (
    kind              varchar(30) not null,
    subject_id        uuid        not null,
    staff_account_id  uuid        not null,
    assigned_at       timestamptz not null,
    primary key (kind, subject_id)
);

create index idx_staff_work_by_staff on staff_work_assignments (staff_account_id, kind);
