package com.sheout.booking.internal;

import com.sheout.dispatch.DriverLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteCheckTest {

    private static final Instant START = Instant.parse("2026-09-26T10:00:00Z");

    /** A drive due north at ~25 km/h, one report every 7 s. */
    private static List<DriverLocation> northward(double km, int everySeconds) {
        List<DriverLocation> points = new ArrayList<>();
        double metresPerReport = 25000.0 / 3600 * everySeconds;
        int reports = (int) Math.ceil(km * 1000 / metresPerReport);
        for (int i = 0; i <= reports; i++) {
            double metres = Math.min(km * 1000, i * metresPerReport);
            points.add(new DriverLocation(17.40 + metres / 111_000, 78.45, START.plusSeconds((long) i * everySeconds)));
        }
        return points;
    }

    private static Instant end(List<DriverLocation> trail) {
        return trail.get(trail.size() - 1).recordedAt().plusSeconds(2);
    }

    @Test
    void measuresTheDistanceDriven() {
        List<DriverLocation> trail = northward(4.0, 7);
        RouteCheck.Measurement m = RouteCheck.measure(trail, START, end(trail));
        assertThat(m.actualKm().doubleValue()).isBetween(3.95, 4.05);
    }

    @Test
    void standingStillIsNotDistance() {
        List<DriverLocation> trail = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            // GPS wobbling a few metres around one spot for 7 minutes.
            trail.add(new DriverLocation(17.40 + (i % 2) * 0.00005, 78.45, START.plusSeconds(i * 7L)));
        }
        assertThat(RouteCheck.measure(trail, START, end(trail)).actualKm()).isEqualByComparingTo("0");
    }

    @Test
    void aTrailWithLongSilencesIsNotMeasured() {
        List<DriverLocation> full = northward(6.0, 7);
        // Phone asleep for the middle 70% of the trip.
        List<DriverLocation> gappy = new ArrayList<>(full.subList(0, 5));
        gappy.addAll(full.subList(full.size() - 5, full.size()));
        RouteCheck.Measurement m = RouteCheck.measure(gappy, START, end(full));
        assertThat(m.measured()).isFalse();
        assertThat(RouteCheck.worthReview(new BigDecimal("6.0"), m, 25, 0.5)).isFalse();
    }

    @Test
    void tooFewReportsIsNotMeasured() {
        List<DriverLocation> two = northward(4.0, 7).subList(0, 2);
        assertThat(RouteCheck.measure(two, START, two.get(1).recordedAt()).measured()).isFalse();
    }

    @Test
    void aMuchShorterDriveIsFlaggedAndOneThatMatchesIsNot() {
        List<DriverLocation> shortcut = northward(3.0, 7);
        List<DriverLocation> honest = northward(5.8, 7);
        BigDecimal quoted = new BigDecimal("6.0");
        assertThat(RouteCheck.worthReview(quoted, RouteCheck.measure(shortcut, START, end(shortcut)), 25, 0.5)).isTrue();
        assertThat(RouteCheck.worthReview(quoted, RouteCheck.measure(honest, START, end(honest)), 25, 0.5)).isFalse();
    }

    @Test
    void aShortTripIsNotFlaggedForASmallAbsoluteDifference() {
        // 30% short, but only 0.3 km - GPS and road-snapping noise territory.
        List<DriverLocation> trail = northward(0.7, 7);
        assertThat(RouteCheck.worthReview(new BigDecimal("1.0"), RouteCheck.measure(trail, START, end(trail)), 25, 0.5)).isFalse();
    }
}
