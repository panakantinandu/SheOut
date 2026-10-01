package com.sheout.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What trip watch has noticed about live trips, for the operations console.
 * See booking's TripWatchService for what each kind means and its thresholds.
 */
public interface TripAlertsApi {

    /** Open alerts, oldest first. */
    List<TripAlert> openAlerts();

    /** Every alert ever raised on one trip, open or not, newest first. */
    List<TripAlert> alertsFor(UUID bookingId);

    /**
     * An operator has checked on it. Recorded with her note; the trip itself
     * is untouched. The same condition on the same trip then stays quiet for
     * a while (sheout.trip-watch.snooze-after-check-minutes), and comes back
     * if it still holds after that.
     */
    boolean acknowledge(UUID alertId, UUID adminAccountId, String note);

    enum Kind {
        /** In progress far longer than the distance should take. */
        OVERDUE,
        /** The partner's phone has stopped reporting during the trip. */
        LOCATION_SILENT,
        /** Barely moved for several minutes mid-trip, away from the drop. */
        STOPPED,
        /** Accepted long ago and never started. */
        STUCK_ACCEPTED,
        /** Her phone reported a physically impossible jump during the trip. */
        GPS_JUMP
    }

    /** resolvedBy is null for an alert that cleared by itself. */
    record TripAlert(UUID id, UUID bookingId, Kind kind, String detail, Instant raisedAt,
                     Instant resolvedAt, UUID resolvedBy, String note) {
    }
}
