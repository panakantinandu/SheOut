-- When a start-of-shift check was held for a person (three mismatches in a
-- row). Kept after the check is decided, so the weekly count of holds -
-- and how many turned out to be her - can be read back. Null for every
-- check that was never held, including every check from before this.
alter table shift_checks add column held_at timestamptz;

create index idx_shift_checks_held_at on shift_checks (held_at) where held_at is not null;
