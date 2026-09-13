package com.sheout.booking.internal.fare;

import java.util.List;

/**
 * The shape of a route, for drawing on a map.
 * <p>
 * Separate from {@link RouteEstimate}, which pricing uses. Pricing needs two
 * numbers and asks OSRM for no geometry at all; navigation needs the line
 * and does not care what the trip costs. Keeping them apart means every fare
 * quote is not paying to download a few hundred coordinates it will discard.
 * <p>
 * {@code points} is empty when the router could not be reached. That is a
 * deliberate absence, not a degraded line: this codebase does not draw a
 * straight line between two points and let it be read as a road, for the
 * same reason the driver marker never interpolates between fixes. A caller
 * with no points should say the route is unavailable, not invent one.
 */
public record RoutePath(
        List<RoutePoint> points,
        double distanceKm,
        double durationMinutes
) {

    public static RoutePath unavailable() {
        return new RoutePath(List.of(), 0, 0);
    }

    public boolean isAvailable() {
        return !points.isEmpty();
    }

    /** One point on the line. Lat/lng in that order, unlike OSRM's own wire format. */
    public record RoutePoint(double lat, double lng) {
    }
}
