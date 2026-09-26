package com.sheout.booking.internal;

import com.sheout.dispatch.DriverLocation;
import com.sheout.sharedkernel.geo.GeoDistance;

import java.time.Duration;
import java.time.Instant;

/**
 * Server-side completion rule for the driver's last trusted GPS fix.
 * The booking owns the authoritative drop-off coordinates; this class only
 * decides whether the supplied location is usable for the completion check.
 */
final class DropoffGeofence {

    private final double radiusKm;
    private final Duration maxLocationAge;

    DropoffGeofence(double radiusMetres, Duration maxLocationAge) {
        this.radiusKm = radiusMetres / 1000.0;
        this.maxLocationAge = maxLocationAge;
    }

    Decision check(DriverLocation location, double dropLat, double dropLng, Instant now) {
        if (location == null
                || !validCoordinate(location.lat(), location.lng())
                || !validCoordinate(dropLat, dropLng)
                || location.recordedAt() == null
                || location.recordedAt().isBefore(now.minus(maxLocationAge))
                || location.recordedAt().isAfter(now)) {
            return Decision.LOCATION_UNAVAILABLE;
        }
        double distanceKm = GeoDistance.haversineKm(
                location.lat(), location.lng(), dropLat, dropLng);
        if (!Double.isFinite(distanceKm)) {
            return Decision.LOCATION_UNAVAILABLE;
        }
        return distanceKm <= radiusKm ? Decision.AT_DROP_OFF : Decision.OUTSIDE_DROP_OFF;
    }

    enum Decision {
        AT_DROP_OFF,
        OUTSIDE_DROP_OFF,
        LOCATION_UNAVAILABLE
    }

    private static boolean validCoordinate(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng)
                && Math.abs(lat) <= 90 && Math.abs(lng) <= 180;
    }
}
