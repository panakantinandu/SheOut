package com.sheout.booking.internal.fare;

import java.util.List;

/**
 * The shape of a route, for drawing on a map, and its turns, for in-app
 * navigation.
 * <p>
 * Separate from {@link RouteEstimate}, which pricing uses. Pricing needs two
 * numbers and asks OSRM for no geometry at all; navigation needs the line
 * and the turns and does not care what the trip costs. Keeping them apart
 * means every fare quote is not paying to download a few hundred coordinates
 * it will discard.
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
        double durationMinutes,
        List<RouteStep> steps
) {

    public RoutePath(List<RoutePoint> points, double distanceKm, double durationMinutes) {
        this(points, distanceKm, durationMinutes, List.of());
    }

    public static RoutePath unavailable() {
        return new RoutePath(List.of(), 0, 0, List.of());
    }

    public boolean isAvailable() {
        return !points.isEmpty();
    }

    /** One point on the line. Lat/lng in that order, unlike OSRM's own wire format. */
    public record RoutePoint(double lat, double lng) {
    }

    /**
     * One manoeuvre, as OSRM describes it: where it happens, what kind it is
     * (turn, roundabout, arrive ...), which way (left, slight right ...), and
     * the road she is on after it. The words are the app's to write, in her
     * language; this carries only the facts.
     *
     * @param type      OSRM maneuver type, e.g. "turn", "roundabout", "arrive"
     * @param modifier  OSRM maneuver modifier, e.g. "left", "slight right"; null when none
     * @param name      the road after the manoeuvre; empty when unnamed
     * @param exit      the roundabout exit number; null otherwise
     * @param distanceMetres how far this step runs after its manoeuvre
     * @param durationSeconds how long that takes
     */
    public record RouteStep(String type, String modifier, String name, Integer exit,
                            double distanceMetres, double durationSeconds, double lat, double lng) {
    }
}
