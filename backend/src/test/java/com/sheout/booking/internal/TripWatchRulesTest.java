package com.sheout.booking.internal;

import com.sheout.booking.BookingStatus;
import com.sheout.booking.TripAlertsApi.Kind;
import com.sheout.dispatch.DriverLocation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TripWatchRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-01T14:00:00Z");
    private static final double DROP_LAT = 17.4400, DROP_LNG = 78.3900;
    private static final TripWatchRules.Thresholds TH = TripWatchRules.Thresholds.defaults();

    private static Instant ago(int minutes) {
        return NOW.minus(Duration.ofMinutes(minutes));
    }

    private static DriverLocation at(double lat, double lng, Instant when) {
        return new DriverLocation(lat, lng, when);
    }

    /** A partner reporting every 30 s from one spot for the last `minutes`. */
    private static List<DriverLocation> parkedFor(int minutes, double lat, double lng) {
        List<DriverLocation> out = new ArrayList<>();
        for (int s = minutes * 60; s >= 0; s -= 30) {
            out.add(at(lat + (s % 60 == 0 ? 0.00001 : 0), lng, NOW.minusSeconds(s)));
        }
        return out;
    }

    private static Map<Kind, String> riding(int startedMinutesAgo, double km, DriverLocation fix, List<DriverLocation> trail, int jumps) {
        return TripWatchRules.evaluate(new TripWatchRules.TripFacts(BookingStatus.IN_PROGRESS, ago(startedMinutesAgo + 5),
                ago(startedMinutesAgo), km, DROP_LAT, DROP_LNG, fix, trail, jumps), NOW, TH);
    }

    @Test
    void anOrdinaryTripRaisesNothing() {
        DriverLocation fix = at(17.42, 78.42, NOW.minusSeconds(7));
        assertThat(riding(10, 8, fix, List.of(at(17.41, 78.44, ago(9)), at(17.415, 78.43, ago(5)), fix), 0)).isEmpty();
    }

    @Test
    void aTripFarOverItsTimeIsOverdue() {
        // 8 km at 18 km/h is ~27 min; 1.5x plus 15 is ~55.
        DriverLocation fix = at(17.42, 78.42, NOW.minusSeconds(7));
        assertThat(riding(50, 8, fix, List.of(fix), 0)).doesNotContainKey(Kind.OVERDUE);
        assertThat(riding(60, 8, fix, List.of(fix), 0)).containsKey(Kind.OVERDUE);
    }

    @Test
    void aLongStopAwayFromTheDropIsFlagged() {
        List<DriverLocation> trail = parkedFor(10, 17.40, 78.45);
        Map<Kind, String> out = riding(20, 8, trail.get(trail.size() - 1), trail, 0);
        assertThat(out).containsKey(Kind.STOPPED);
    }

    @Test
    void aRedLightIsNotAStop() {
        List<DriverLocation> trail = new ArrayList<>(List.of(at(17.39, 78.46, ago(8)), at(17.395, 78.455, ago(6))));
        trail.addAll(parkedFor(2, 17.40, 78.45));
        assertThat(riding(20, 8, trail.get(trail.size() - 1), trail, 0)).doesNotContainKey(Kind.STOPPED);
    }

    @Test
    void stoppingAtTheDropIsNotAStop() {
        List<DriverLocation> trail = parkedFor(10, DROP_LAT, DROP_LNG);
        assertThat(riding(20, 8, trail.get(trail.size() - 1), trail, 0)).doesNotContainKey(Kind.STOPPED);
    }

    @Test
    void aQuietPhoneMidTripIsFlaggedButNotRightAfterStarting() {
        assertThat(riding(10, 8, at(17.42, 78.42, ago(5)), List.of(), 0)).containsKey(Kind.LOCATION_SILENT);
        assertThat(riding(2, 8, at(17.42, 78.42, ago(5)), List.of(), 0)).doesNotContainKey(Kind.LOCATION_SILENT);
    }

    @Test
    void acceptedAndNeverStartedIsStuck() {
        var facts = new TripWatchRules.TripFacts(BookingStatus.ACCEPTED, ago(50), null, 8.0, DROP_LAT, DROP_LNG,
                at(17.42, 78.42, NOW.minusSeconds(7)), List.of(), 0);
        assertThat(TripWatchRules.evaluate(facts, NOW, TH)).containsOnlyKeys(Kind.STUCK_ACCEPTED);
    }

    @Test
    void impossibleJumpsAreReported() {
        DriverLocation fix = at(17.42, 78.42, NOW.minusSeconds(7));
        assertThat(riding(10, 8, fix, List.of(fix), 2)).containsKey(Kind.GPS_JUMP);
    }

    @Test
    void finishedTripsAreNotWatched() {
        var facts = new TripWatchRules.TripFacts(BookingStatus.COMPLETED, ago(200), ago(190), 8.0, DROP_LAT, DROP_LNG,
                null, List.of(), 3);
        assertThat(TripWatchRules.evaluate(facts, NOW, TH)).isEmpty();
    }
}
