-- Room for the NO_DRIVERS_AVAILABLE status.
--
-- It is exactly 20 characters, so it fits varchar(20) with nothing to
-- spare. That is not a margin, it is a coincidence: the next status anybody
-- adds with a longer name would fail at INSERT time, in production, on a
-- code path that compiled and passed schema validation perfectly well.
--
-- Widened rather than left at the edge because the failure mode is so
-- poor. Hibernate validates the length it was told about, not the length of
-- the longest enum constant, so nothing in the build would have caught it.
ALTER TABLE bookings ALTER COLUMN status TYPE VARCHAR(30);

COMMENT ON COLUMN bookings.status IS
    'BookingStatus enum. NO_DRIVERS_AVAILABLE means dispatch searched, found nobody and stopped - distinct from CANCELLED, which means a person called it off. See BookingStatus for why the two must not be merged.';
