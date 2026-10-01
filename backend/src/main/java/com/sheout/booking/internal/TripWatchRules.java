package com.sheout.booking.internal;

import com.sheout.booking.BookingStatus;
import com.sheout.booking.TripAlertsApi.Kind;
import com.sheout.dispatch.DriverLocation;
import com.sheout.sharedkernel.geo.GeoDistance;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What about a live trip deserves a person's look, from facts alone - no
 * database, so every threshold can be tested.
 * <p>
 * Each rule is deliberately slow to fire. One noisy signal - a phone in a
 * pocket in a tunnel, a long red light - must not page an operator, and
 * nothing here ever cancels a trip; it raises a question for a person.
 */
final class TripWatchRules {

    /** Thresholds, all configurable - see TripWatchService. */
    record Thresholds(
            Duration stuckAcceptedAfter,
            Duration locationSilentAfter,
            Duration stoppedFor,
            double stoppedWithinMetres,
            double dropRadiusMetres,
            double overdueFactor,
            Duration overdueSlack,
            double assumedKmh
    ) {
        static Thresholds defaults() {
            return new Thresholds(Duration.ofMinutes(45), Duration.ofMinutes(3), Duration.ofMinutes(8), 80, 250,
                    1.5, Duration.ofMinutes(15), 18);
        }
    }

    /** Everything the rules look at about one trip. trail is the partner's reports since the trip started. */
    record TripFacts(
            BookingStatus status,
            Instant acceptedAt,
            Instant startedAt,
            Double quotedDistanceKm,
            double dropLat,
            double dropLng,
            DriverLocation lastFix,
            List<DriverLocation> trail,
            int implausibleJumps
    ) {
    }

    private TripWatchRules() {
    }

    /** The conditions that hold right now, each with a line an operator can read. */
    static Map<Kind, String> evaluate(TripFacts t, Instant now, Thresholds th) {
        Map<Kind, String> out = new EnumMap<>(Kind.class);
        boolean accepted = t.status() == BookingStatus.ACCEPTED;
        boolean riding = t.status() == BookingStatus.IN_PROGRESS;
        if (!accepted && !riding) {
            return out;
        }

        if (accepted && t.acceptedAt() != null && t.acceptedAt().isBefore(now.minus(th.stuckAcceptedAfter()))) {
            out.put(Kind.STUCK_ACCEPTED, "Accepted " + minutes(Duration.between(t.acceptedAt(), now))
                    + " min ago and not started");
        }

        // Only once the phase has run long enough for a quiet phone to mean something.
        Instant phaseStart = riding ? t.startedAt() : t.acceptedAt();
        if (phaseStart != null && phaseStart.isBefore(now.minus(th.locationSilentAfter()))) {
            if (t.lastFix() == null) {
                out.put(Kind.LOCATION_SILENT, "No position from the partner's phone");
            } else if (t.lastFix().recordedAt().isBefore(now.minus(th.locationSilentAfter()))) {
                out.put(Kind.LOCATION_SILENT, "Partner's phone silent for "
                        + minutes(Duration.between(t.lastFix().recordedAt(), now)) + " min");
            }
        }

        if (riding && t.startedAt() != null) {
            double km = t.quotedDistanceKm() == null ? 5 : t.quotedDistanceKm();
            long expected = Math.max(10, Math.round(km / th.assumedKmh() * 60));
            Duration allowed = Duration.ofMinutes(Math.round(expected * th.overdueFactor())).plus(th.overdueSlack());
            Duration elapsed = Duration.between(t.startedAt(), now);
            if (elapsed.compareTo(allowed) > 0) {
                out.put(Kind.OVERDUE, "On the trip " + minutes(elapsed) + " min; about " + expected
                        + " min expected for " + Math.round(km * 10) / 10.0 + " km");
            }

            String stopped = stopped(t, now, th);
            if (stopped != null) {
                out.put(Kind.STOPPED, stopped);
            }
        }

        if (t.implausibleJumps() > 0) {
            out.put(Kind.GPS_JUMP, t.implausibleJumps() + " impossible position jump"
                    + (t.implausibleJumps() == 1 ? "" : "s") + " from her phone in the last day");
        }
        return out;
    }

    /**
     * Every report in the last stoppedFor minutes within a small circle, with
     * enough of them to be sure the phone was reporting, away from the drop.
     * A trip that has not been going that long cannot have stopped for it.
     */
    private static String stopped(TripFacts t, Instant now, Thresholds th) {
        if (t.trail() == null || t.trail().isEmpty() || t.startedAt().isAfter(now.minus(th.stoppedFor()))) {
            return null;
        }
        Instant from = now.minus(th.stoppedFor());
        List<DriverLocation> window = t.trail().stream().filter(p -> !p.recordedAt().isBefore(from)).toList();
        if (window.size() < 3) {
            return null;
        }
        // The window must reach back near its start, or a fresh burst of reports looks like a stop.
        if (window.get(0).recordedAt().isAfter(from.plus(Duration.ofMinutes(2)))) {
            return null;
        }
        DriverLocation last = window.get(window.size() - 1);
        for (DriverLocation p : window) {
            if (GeoDistance.haversineKm(p.lat(), p.lng(), last.lat(), last.lng()) * 1000 > th.stoppedWithinMetres()) {
                return null;
            }
        }
        double toDropM = GeoDistance.haversineKm(last.lat(), last.lng(), t.dropLat(), t.dropLng()) * 1000;
        if (toDropM <= th.dropRadiusMetres()) {
            return null;
        }
        return "Not moved for " + minutes(th.stoppedFor()) + "+ min, " + Math.round(toDropM / 100) / 10.0 + " km from the drop";
    }

    private static long minutes(Duration d) {
        return d.toMinutes();
    }
}
