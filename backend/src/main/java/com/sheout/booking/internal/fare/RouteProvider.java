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
}
