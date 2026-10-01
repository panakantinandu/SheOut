-- When the current search for a partner began. Usually the booking's own
-- creation time; later when a partner who had taken the trip dropped it and
-- the search started again. The stale-search sweeper reads this, not
-- created_at, or a re-opened booking would be ended as stale at once.
alter table bookings add column search_started_at timestamptz;
update bookings set search_started_at = created_at where search_started_at is null;
create index idx_bookings_status_search_started on bookings (status, search_started_at);
