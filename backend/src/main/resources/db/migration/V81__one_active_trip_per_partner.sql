-- A partner can hold at most one active trip. Until now this rested on
-- application checks (an offer slot in Redis, and a re-check at accept);
-- nothing in the database stopped a second assignment. See
-- docs/parcel-dispatch-audit.md, B.3.
--
-- If any partner already holds two active trips, this migration stops with
-- the list instead of guessing which one to end: the deploy fails, nothing is
-- changed, and an operator settles those trips first. Run the read-only check
-- in docs/parcel-proof-release.md on production before deploying.
do $$
declare
    offenders text;
begin
    select string_agg(driver_id::text || ' (' || n || ' trips)', ', ')
      into offenders
      from (select driver_id, count(*) as n
              from bookings
             where driver_id is not null
               and status in ('MATCHED', 'ACCEPTED', 'IN_PROGRESS')
             group by driver_id
            having count(*) > 1) dup;
    if offenders is not null then
        raise exception 'V81: partners with more than one active trip: %. Settle those trips, then deploy again.', offenders;
    end if;
end $$;

create unique index uq_bookings_one_active_trip_per_partner
    on bookings (driver_id)
    where status in ('MATCHED', 'ACCEPTED', 'IN_PROGRESS');
