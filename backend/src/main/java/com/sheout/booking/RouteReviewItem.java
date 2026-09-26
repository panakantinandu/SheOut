package com.sheout.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A trip the route check flagged: driven measurably shorter than it was
 * quoted. Everything an operator needs to judge it - both distances, who
 * ended the trip and where, and any reason the partner gave - and nothing
 * that decides for them.
 * <p>
 * quotedRouted is false when the quote itself was the straight-line
 * fallback estimate rather than a real road route, which makes the
 * comparison weaker; the console says so.
 */
public record RouteReviewItem(
        UUID bookingId,
        UUID customerId,
        UUID driverId,
        BookingCategory category,
        GeoAddress pickup,
        GeoAddress drop,
        BigDecimal fare,
        BigDecimal quotedDistanceKm,
        boolean quotedRouted,
        BigDecimal actualDistanceKm,
        int routePoints,
        TripEndedBy endedBy,
        Integer endedMetresFromDrop,
        DropOffDeviationReason dropOffReason,
        String dropOffNote,
        Instant startedAt,
        Instant completedAt,
        Instant flaggedAt,
        Instant reviewedAt,
        String reviewNote
) {
}
