-- Home and Work become places on the map, not sentences.
--
-- They were free text: "Flat 4, Kavuri Hills" is something a person reads,
-- and nothing a booking can use. Every other address in this system is a
-- GeoAddress - a label with a latitude and longitude - which is what lets a
-- pickup be set with one tap instead of searched for again.
--
-- The existing text stays as the label, so nothing she typed is lost; the
-- coordinates are null until she picks the place on the map. A saved address
-- with no coordinates is still shown, and still cannot be used as a pickup -
-- which is exactly what it was worth before.
alter table customer_profiles add column home_lat double precision;
alter table customer_profiles add column home_lng double precision;
alter table customer_profiles add column work_lat double precision;
alter table customer_profiles add column work_lng double precision;
