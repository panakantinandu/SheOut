package com.sheout.booking.internal;

import com.sheout.dispatch.DriverLocation;
import com.sheout.sharedkernel.geo.GeoDistance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The distance a partner actually drove, from her own location reports, and
 * whether it is short enough against the quote for a person to look at.
 * <p>
 * A FLAG, NEVER A VERDICT. A shorter drive than quoted has innocent readings
 * - the rider asked to get out early, a road the quote did not know about -
 * and guilty ones, and nothing here can tell them apart. So nothing here
 * changes a fare or counts against anybody: the trip goes on a list, with
 * both distances and any reason she gave, and an operator decides.
 * <p>
 * MEASURED ONLY WHEN IT CAN BE. Reports arrive every ~7 s while the app is
 * open. A phone that slept for five minutes leaves a gap that a straight
 * line would bridge far shorter than the road - exactly the "shortcut" this
 * looks for, manufactured by a battery saver. So a trail with too little
 * coverage is not measured at all (actualKm null), and an unmeasured trip is
 * never flagged: missing data is not evidence.
 */
final class RouteCheck {

    /** Reports closer than this to the last one kept are GPS jitter, not movement. */
    static final double JITTER_METRES = 15;
    /** A silence longer than this between reports is a gap, not a pause. */
    static final Duration GAP = Duration.ofSeconds(45);
    /** Below this share of the trip covered by reports, the trail is not measured. */
    static final double MIN_COVERAGE = 0.6;
    static final int MIN_POINTS = 3;

    record Measurement(BigDecimal actualKm, int points) {
        boolean measured() {
            return actualKm != null;
        }
    }

    private RouteCheck() {
    }

    /** The trail's length between the trip's start and end, or unmeasured. */
    static Measurement measure(List<DriverLocation> trail, Instant startedAt, Instant endedAt) {
        List<DriverLocation> points = trail.stream()
                .filter(p -> p.recordedAt() != null)
                .filter(p -> startedAt == null || !p.recordedAt().isBefore(startedAt.minusSeconds(30)))
                .filter(p -> !p.recordedAt().isAfter(endedAt.plusSeconds(5)))
                .sorted(Comparator.comparing(DriverLocation::recordedAt))
                .toList();
        if (points.size() < MIN_POINTS) {
            return new Measurement(null, points.size());
        }

        double metres = 0;
        DriverLocation kept = points.get(0);
        long gapSeconds = 0;
        DriverLocation previous = points.get(0);
        for (DriverLocation p : points.subList(1, points.size())) {
            long since = Duration.between(previous.recordedAt(), p.recordedAt()).toSeconds();
            if (since > GAP.toSeconds()) {
                gapSeconds += since;
            }
            previous = p;
            double step = GeoDistance.haversineKm(kept.lat(), kept.lng(), p.lat(), p.lng()) * 1000;
            if (step >= JITTER_METRES) {
                metres += step;
                kept = p;
            }
        }

        Instant from = startedAt != null ? startedAt : points.get(0).recordedAt();
        long tripSeconds = Math.max(1, Duration.between(from, endedAt).toSeconds());
        // The time before the first report and after the last counts as
        // uncovered too - a trail that stops halfway is a gap at the end.
        long head = Math.max(0, Duration.between(from, points.get(0).recordedAt()).toSeconds());
        long tail = Math.max(0, Duration.between(points.get(points.size() - 1).recordedAt(), endedAt).toSeconds());
        long uncovered = gapSeconds + (head > GAP.toSeconds() ? head : 0) + (tail > GAP.toSeconds() ? tail : 0);
        double coverage = 1.0 - (double) uncovered / tripSeconds;
        if (coverage < MIN_COVERAGE) {
            return new Measurement(null, points.size());
        }
        return new Measurement(BigDecimal.valueOf(metres / 1000).setScale(2, RoundingMode.HALF_UP), points.size());
    }

    /**
     * Short enough to be worth a person's time: both by share (so a long
     * trip is not flagged for GPS rounding) and by distance (so a 2 km trip
     * is not flagged for 400 m).
     */
    static boolean worthReview(BigDecimal quotedKm, Measurement measurement, double shortfallPercent, double minShortfallKm) {
        if (quotedKm == null || !measurement.measured() || quotedKm.signum() <= 0) {
            return false;
        }
        double quoted = quotedKm.doubleValue();
        double actual = measurement.actualKm().doubleValue();
        double shortfall = quoted - actual;
        return shortfall >= minShortfallKm && shortfall / quoted * 100 >= shortfallPercent;
    }
}
