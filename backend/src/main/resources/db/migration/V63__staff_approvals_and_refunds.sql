-- Phase 3 of the console's security: the actions that move money, change who
-- can do what, change configuration or take data out of the console now need
-- a second person. One asks; another, who holds the approving permission,
-- approves; only then does it happen.

create table staff_approval_requests (
    id                        uuid primary key,
    -- PAYOUT_MARK_PAID, REFUND, STAFF_PRIVILEGED, SECOND_FACTOR_RESET,
    -- INSURANCE_POLICY, EXPORT
    kind                      varchar(40)   not null,
    -- PENDING, APPROVED (an export, until downloaded), EXECUTED, FAILED,
    -- REJECTED, CANCELLED, EXPIRED
    status                    varchar(20)   not null,
    -- One plain sentence: what will happen if this is approved.
    summary                   varchar(500)  not null,
    -- What to do, as JSON, read by the module that does it. Never a secret.
    payload                   varchar(8000) not null,
    -- For a configuration change: what it was before, as JSON. With payload,
    -- this is the configuration's history.
    before_json               varchar(8000),
    approver_permission       varchar(60)   not null,
    target_type               varchar(40),
    target_id                 varchar(80),
    requested_by_staff_id     uuid          not null,
    requested_by_account_id   uuid          not null,
    requested_by_role         varchar(30)   not null,
    reason                    varchar(500),
    created_at                timestamptz   not null,
    expires_at                timestamptz   not null,
    decided_by_staff_id       uuid,
    decided_by_account_id     uuid,
    decided_at                timestamptz,
    decision_note             varchar(500),
    -- The only owner approved her own request: allowed while SheOut has one
    -- owner, and always shown as such.
    self_approved             boolean       not null default false,
    executed_at               timestamptz,
    result_message            varchar(1000),
    -- An export: the exact request it allows, and when it was used (once).
    export_path               varchar(1000),
    export_used_at            timestamptz,
    constraint chk_approval_status check (status in ('PENDING', 'APPROVED', 'EXECUTED', 'FAILED', 'REJECTED',
        'CANCELLED', 'EXPIRED'))
);

create index idx_approvals_status on staff_approval_requests (status, created_at desc);
create index idx_approvals_requester on staff_approval_requests (requested_by_staff_id, created_at desc);
create index idx_approvals_kind on staff_approval_requests (kind, created_at desc);

-- A refund to a rider is a credit to her SheOut wallet, once per refund.
alter table rider_wallet_entries add column refund_id uuid;
create unique index uq_rider_wallet_entries_refund on rider_wallet_entries (refund_id) where entry_type = 'REFUND';
