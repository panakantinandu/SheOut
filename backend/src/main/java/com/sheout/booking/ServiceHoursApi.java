package com.sheout.booking;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * When SheOut takes new bookings, and the operator's controls over it.
 * <p>
 * Booking owns this because the rule it enforces is "may a booking be
 * created now", checked inside requestBooking beside the service-area
 * check. The console reaches it through admin, like every other admin
 * action.
 * <p>
 * A closed service stops NEW bookings only. A trip already under way is
 * never cut off, and a search already running is left to finish: stopping
 * either would strand a woman mid-journey, which is the exact harm the
 * hours exist to prevent.
 */
public interface ServiceHoursApi {

    /** Open or closed right now, and when that changes. */
    ServiceStatus currentStatus();

    ServiceHoursSettings settings();

    /** Sets the daily window and whether it applies. */
    ServiceHoursSettings updateSchedule(ServiceHoursMode mode, LocalTime opensAt, LocalTime closesAt, UUID adminAccountId);

    /**
     * Stops new bookings now. The reason is shown to riders. until is null
     * for "until someone resumes"; otherwise bookings come back on their own.
     */
    ServiceHoursSettings pause(String reason, Instant until, UUID adminAccountId);

    ServiceHoursSettings resume(UUID adminAccountId);

    /** The latest changes, newest first. */
    List<ServiceHoursChange> recentChanges(int limit);

    enum ServiceHoursMode {
        /** Bookings at any hour - SheOut's behaviour before hours existed. */
        ALWAYS_OPEN,
        /** Bookings only inside the daily window. */
        SCHEDULED
    }

    enum ClosedReason {
        OUTSIDE_HOURS,
        PAUSED
    }

    /**
     * reopensAt is null when closed with no known end (a pause without a
     * time). closesAt is null when open with no scheduled end (ALWAYS_OPEN).
     * opensAt/closesAtLocal are the daily window in India time, for the apps
     * to say "6:00 AM - 10:00 PM" in their own language.
     */
    record ServiceStatus(
            boolean open,
            ClosedReason closedReason,
            String pauseReason,
            Instant reopensAt,
            Instant closesAt,
            ServiceHoursMode mode,
            LocalTime opensAt,
            LocalTime closesAtLocal,
            String timeZone
    ) {
    }

    /** paused is false once a timed pause has run out, whatever the row still says. */
    record ServiceHoursSettings(
            ServiceHoursMode mode,
            LocalTime opensAt,
            LocalTime closesAt,
            boolean paused,
            String pauseReason,
            Instant pausedUntil,
            Instant pausedAt,
            UUID pausedBy,
            Instant updatedAt,
            UUID updatedBy
    ) {
    }

    record ServiceHoursChange(UUID changedBy, String summary, Instant at) {
    }
}
