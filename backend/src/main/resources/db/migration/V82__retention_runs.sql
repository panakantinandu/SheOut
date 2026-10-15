-- What each retention run removed (or, in a dry run, would have removed):
-- the evidence that the periods in the Privacy Policy are actually kept.
-- Counts only - never the data itself, nor whose it was. Rows are written
-- once and never changed (RetentionRunEntity is immutable). See
-- privacy.internal.RetentionSweeper and docs/legal-audit.md (A).
create table retention_runs (
    id          uuid primary key,
    task        varchar(60)  not null,
    dry_run     boolean      not null,
    keep_days   integer      not null,
    cutoff      timestamptz  not null,
    matched     integer      not null,
    error       varchar(500),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);

create index idx_retention_runs_created on retention_runs (created_at desc);
