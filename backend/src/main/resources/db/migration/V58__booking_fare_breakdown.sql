-- How a trip's fare was reached, kept with the trip, for the rider's
-- "Fare details" on her receipt: base fare, distance and time charges, the
-- surge and night multipliers, and whether the minimum fare applied.
--
-- Recorded from the quote the booking was priced on. Null for trips booked
-- before this, and cleared when a destination change re-prices the trip -
-- the receipt then shows the total and says the fare was re-priced, rather
-- than a breakdown that no longer adds up to it.
alter table bookings add column fare_base_fare numeric(10, 2);
alter table bookings add column fare_distance_charge numeric(10, 2);
alter table bookings add column fare_time_charge numeric(10, 2);
alter table bookings add column fare_surge_multiplier numeric(6, 3);
alter table bookings add column fare_night_multiplier numeric(6, 3);
alter table bookings add column fare_minimum_applied boolean;
