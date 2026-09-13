package com.sheout.booking.internal.fare;

/**
 * How far and how long a trip actually is, by road.
 * <p>
 * Both numbers come from one routing call because the fare formula needs
 * both, and deriving duration from distance with an assumed speed after the
 * fact would throw away the one thing routing knows that a straight line
 * does not: that some roads are slow.
 */
public record RouteEstimate(double distanceKm, double durationMinutes, Source source) {

    /**
     * Where the numbers came from.
     * <p>
     * Carried through to the quote rather than discarded, because a fare
     * built on a real route and a fare built on a fallback estimate are not
     * equally trustworthy, and anyone reading a price later - an operator
     * handling a complaint, or us tuning the rates - needs to know which
     * they are looking at. Silently substituting one for the other is how a
     * pricing bug becomes invisible.
     */
    public enum Source {
        /** A real road route from OSRM. */
        ROUTED,

        /**
         * Straight-line distance scaled by a detour factor, with duration
         * from an assumed average speed. Used when routing is unavailable -
         * see OsrmRouteProvider for why this exists at all.
         */
        ESTIMATED
    }

    public boolean isRouted() {
        return source == Source.ROUTED;
    }
}
