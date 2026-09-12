package com.sheout.booking.internal;

import com.sheout.booking.GeoAddress;

/**
 * Straight-line distance between two points, in kilometres.
 * <p>
 * Extracted from DistanceBasedFareCalculator, which had it as a private
 * method, once a second caller appeared: the service-area check needs the
 * same measurement. Two copies of a haversine is exactly the kind of
 * duplication that drifts silently - a rounding change or an earth-radius
 * change in one would quietly price trips on one definition of a kilometre
 * and admit them on another.
 * <p>
 * FLAGGED, as it is at the fare calculator too: this is as-the-crow-flies,
 * not routed road distance. For a service boundary that is the right
 * measure anyway - "within 150km of the city" is a radius, not a drive -
 * but the fare it feeds is still an approximation pending a routing
 * provider.
 */
public final class GeoDistance {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {
    }

    public static double haversineKm(GeoAddress from, GeoAddress to) {
        return haversineKm(from.lat(), from.lng(), to.lat(), to.lng());
    }

    public static double haversineKm(double fromLat, double fromLng, double toLat, double toLng) {
        double lat1 = Math.toRadians(fromLat);
        double lat2 = Math.toRadians(toLat);
        double deltaLat = Math.toRadians(toLat - fromLat);
        double deltaLng = Math.toRadians(toLng - fromLng);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
