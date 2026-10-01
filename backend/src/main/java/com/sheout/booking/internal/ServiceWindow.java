package com.sheout.booking.internal;

import com.sheout.booking.ServiceHoursApi.ClosedReason;
import com.sheout.booking.ServiceHoursApi.ServiceHoursMode;
import com.sheout.booking.ServiceHoursApi.ServiceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Open or closed at an instant - the arithmetic only, no database, so every
 * edge is testable.
 * <p>
 * The window is India time, whatever the server's own zone: Render runs in
 * UTC, and "10 PM" means 10 PM in Hyderabad. A window may cross midnight
 * (05:00 to 01:00), and one whose ends are equal is treated as no window at
 * all - the console refuses to save one, but a row edited by hand must not
 * close the service for good.
 */
final class ServiceWindow {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private ServiceWindow() {
    }

    static ServiceStatus statusAt(Instant now, ServiceHoursMode mode, LocalTime opens, LocalTime closes,
                                  boolean pausedNow, String pauseReason, Instant pausedUntil) {
        boolean scheduled = mode == ServiceHoursMode.SCHEDULED && !opens.equals(closes);
        if (pausedNow) {
            // When the pause ends the schedule still applies: a pause until
            // 11 PM under a 10 PM close does not reopen until the morning.
            Instant reopens = pausedUntil == null ? null : scheduled ? nextOpening(pausedUntil, opens, closes) : pausedUntil;
            return new ServiceStatus(false, ClosedReason.PAUSED, pauseReason, reopens, null, mode, opens, closes, INDIA.getId());
        }
        if (scheduled && !inWindow(now, opens, closes)) {
            return new ServiceStatus(false, ClosedReason.OUTSIDE_HOURS, null, nextOpening(now, opens, closes), null,
                    mode, opens, closes, INDIA.getId());
        }
        return new ServiceStatus(true, null, null, null, scheduled ? endOfWindow(now, closes) : null,
                mode, opens, closes, INDIA.getId());
    }

    static boolean inWindow(Instant at, LocalTime opens, LocalTime closes) {
        LocalTime t = at.atZone(INDIA).toLocalTime();
        if (opens.isBefore(closes)) {
            return !t.isBefore(opens) && t.isBefore(closes);
        }
        // Crosses midnight: open from `opens` to the end of the day, and from
        // the start of the next until `closes`.
        return !t.isBefore(opens) || t.isBefore(closes);
    }

    /** The first opening at or after `from`; `from` itself when already open. */
    static Instant nextOpening(Instant from, LocalTime opens, LocalTime closes) {
        if (inWindow(from, opens, closes)) {
            return from;
        }
        return firstAfter(from, opens);
    }

    /** When the window `at` sits in ends. */
    static Instant endOfWindow(Instant at, LocalTime closes) {
        return firstAfter(at, closes);
    }

    /** The first time-of-day `time` strictly after `from`, in India. */
    private static Instant firstAfter(Instant from, LocalTime time) {
        LocalDate day = from.atZone(INDIA).toLocalDate();
        ZonedDateTime candidate = day.atTime(time).atZone(INDIA);
        if (!candidate.toInstant().isAfter(from)) {
            candidate = day.plusDays(1).atTime(time).atZone(INDIA);
        }
        return candidate.toInstant();
    }
}
