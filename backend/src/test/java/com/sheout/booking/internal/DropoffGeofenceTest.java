package com.sheout.booking.internal;

import com.sheout.dispatch.internal.DriverLocation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropoffGeofenceTest {

    private static final Instant NOW = Instant.parse("2026-09-22T15:00:00Z");
    private final DropoffGeofence geofence = new DropoffGeofence(100, Duration.ofSeconds(30));

    @Test
    void exactDropoffIsAllowed() {
        assertEquals(DropoffGeofence.Decision.AT_DROP_OFF,
                geofence.check(location(17.3850, 78.4867, 0), 17.3850, 78.4867, NOW));
    }

    @Test
    void locationInsideConfiguredRadiusIsAllowed() {
        assertEquals(DropoffGeofence.Decision.AT_DROP_OFF,
                geofence.check(location(17.3855, 78.4867, 0), 17.3850, 78.4867, NOW));
    }

    @Test
    void locationOutsideRadiusIsRejected() {
        assertEquals(DropoffGeofence.Decision.OUTSIDE_DROP_OFF,
                geofence.check(location(17.3870, 78.4867, 0), 17.3850, 78.4867, NOW));
    }

    @Test
    void missingAndStaleLocationsAreRejected() {
        assertEquals(DropoffGeofence.Decision.LOCATION_UNAVAILABLE,
                geofence.check(null, 17.3850, 78.4867, NOW));
        assertEquals(DropoffGeofence.Decision.LOCATION_UNAVAILABLE,
                geofence.check(location(17.3850, 78.4867, 31), 17.3850, 78.4867, NOW));
    }

    @Test
    void invalidCoordinatesAreRejected() {
        assertEquals(DropoffGeofence.Decision.LOCATION_UNAVAILABLE,
                geofence.check(location(Double.NaN, 78.4867, 0), 17.3850, 78.4867, NOW));
        assertEquals(DropoffGeofence.Decision.LOCATION_UNAVAILABLE,
                geofence.check(location(17.3850, 78.4867, 0), 91, 78.4867, NOW));
    }

    private static DriverLocation location(double lat, double lng, long ageSeconds) {
        return new DriverLocation(lat, lng, NOW.minusSeconds(ageSeconds));
    }
}
