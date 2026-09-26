package com.sheout.booking.internal;

import com.sheout.dispatch.TripTrailApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Runs the route check on a trip as it ends and writes the outcome onto the
 * booking. See RouteCheck for what is measured and why a flag is only ever a
 * request for a person to look.
 */
@Component
public class TripRouteChecker {

    private final TripTrailApi trails;
    private final double shortfallPercent;
    private final double minShortfallKm;

    public TripRouteChecker(TripTrailApi trails,
                            // Driven this much shorter than quoted, as a share
                            // of the quote, before a person is asked to look.
                            @Value("${sheout.booking.route-check.shortfall-percent:25}") double shortfallPercent,
                            // ...and by at least this much, so short trips are
                            // not flagged for GPS rounding.
                            @Value("${sheout.booking.route-check.min-shortfall-km:0.5}") double minShortfallKm) {
        this.trails = trails;
        this.shortfallPercent = shortfallPercent;
        this.minShortfallKm = minShortfallKm;
    }

    /** For tests and callers with no trail source: every trip reads as unmeasured and none is flagged. */
    static TripRouteChecker withoutTrails() {
        return new TripRouteChecker(bookingId -> List.of(), 25, 0.5);
    }

    void check(BookingEntity booking, Instant endedAt) {
        RouteCheck.Measurement measurement = RouteCheck.measure(trails.trail(booking.getId()), booking.getStartedAt(), endedAt);
        boolean flag = RouteCheck.worthReview(booking.getQuotedDistanceKm(), measurement, shortfallPercent, minShortfallKm);
        booking.recordRouteCheck(measurement.actualKm(), measurement.points(), flag, endedAt);
    }
}
