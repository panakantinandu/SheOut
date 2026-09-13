package com.sheout.booking.internal.fare;

import com.sheout.booking.GeoAddress;

/**
 * The swap point for road routing, the same way FareCalculator is the swap
 * point for pricing.
 * <p>
 * An interface rather than a direct OSRM call so that self-hosting OSRM, or
 * moving to a commercial router, is a new implementation and nothing else.
 * That move is expected - see OsrmRouteProvider.
 * <p>
 * Never throws and never returns null. A router being unreachable must not
 * stop somebody booking a trip, so the contract is that an estimate always
 * comes back and says how it was arrived at.
 */
public interface RouteProvider {

    RouteEstimate route(GeoAddress pickup, GeoAddress drop);

    /**
     * The same road route, but with its shape, for drawing on a partner's
     * navigation map.
     * <p>
     * A second method rather than a field on {@link RouteEstimate} because
     * the two callers want opposite things: pricing asks for no geometry and
     * should not pay to transfer it, and navigation wants the line and not
     * the price. Asking OSRM for geometry on every fare quote would make
     * every booking slower for a few hundred coordinates nobody reads.
     * <p>
     * Unlike {@link #route}, this has no fallback. A missing route is
     * reported as missing - see {@link RoutePath}.
     * <p>
     * Defaulted to "unavailable" rather than left abstract, because drawing
     * a line is optional and pricing a trip is not: a router that can do the
     * one but not the other is still a usable router, and the map simply
     * says it has no route to show. It also keeps this a functional
     * interface, which the fare tests rely on to stub a fixed distance in
     * one line.
     */
    default RoutePath routePath(GeoAddress from, GeoAddress to) {
        return RoutePath.unavailable();
    }
}
