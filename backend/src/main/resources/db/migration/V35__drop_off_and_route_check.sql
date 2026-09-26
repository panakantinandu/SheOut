-- What was promised and what happened, recorded on the trip.
--
-- QUOTED: the road distance the fare was priced on, kept at booking time.
-- The fare alone cannot be turned back into a distance once surge, night
-- rates and minimum fares have been applied.
alter table bookings
    add column quoted_distance_km numeric(8, 2),
    add column quoted_distance_routed boolean;

-- HOW IT ENDED. Who ended it (the partner at the drop, or the rider "end
-- trip here"), where the partner was, how far that was from the drop, and -
-- when she ended it away from the drop - the reason she had to give.
alter table bookings
    add column completed_by varchar(10),
    add column completion_lat double precision,
    add column completion_lng double precision,
    add column completion_distance_from_drop_m integer,
    add column drop_deviation_reason varchar(40),
    add column drop_deviation_note varchar(500);

-- THE ROUTE CHECK. The distance actually driven, from the partner's own
-- location reports during the trip; null when those reports were too sparse
-- to measure a route from (see RouteCheck). A trip measurably shorter than
-- quoted is flagged for a person to look at - never re-priced, never held
-- against anybody automatically - and stays flagged until an operator clears
-- it with a note.
alter table bookings
    add column actual_distance_km numeric(8, 2),
    add column route_points integer,
    add column route_flagged_at timestamptz,
    add column route_reviewed_at timestamptz,
    add column route_reviewed_by uuid,
    add column route_review_note varchar(1000);

create index idx_bookings_route_review on bookings (route_flagged_at)
    where route_flagged_at is not null and route_reviewed_at is null;
