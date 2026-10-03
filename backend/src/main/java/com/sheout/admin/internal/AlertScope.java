package com.sheout.admin.internal;

import com.sheout.booking.BookingApi;
import com.sheout.booking.TripAlertsApi;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosApi;
import com.sheout.notifications.SosStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * What the safety desk may see: a trip with an SOS or trip alert that is
 * open, or closed less than 30 minutes ago (STAFF_ALERT_ACCESS_MINUTES), and
 * the people in it. Nothing else - a responder has no business with trips
 * that are going fine.
 * <p>
 * The grace period is for the call after an alert is closed ("are you home
 * safe?"), not for browsing. Roles that see every trip (trips.view) or every
 * account (users.view) are not held to this; callers check that first.
 */
@Component
class AlertScope {

    private final SosApi sos;
    private final TripAlertsApi tripAlerts;
    private final BookingApi bookings;
    private final Duration grace;

    AlertScope(SosApi sos, TripAlertsApi tripAlerts, BookingApi bookings,
               @Value("${sheout.staff.alert-access-minutes:30}") long graceMinutes) {
        this.sos = sos;
        this.tripAlerts = tripAlerts;
        this.bookings = bookings;
        this.grace = Duration.ofMinutes(graceMinutes);
    }

    boolean bookingInScope(UUID bookingId) {
        if (bookingId == null) {
            return false;
        }
        Instant since = Instant.now().minus(grace);
        boolean sosOpen = sos.findByBookingId(bookingId).stream().anyMatch(a -> live(a, since));
        boolean tripAlertOpen = tripAlerts.alertsFor(bookingId).stream()
                .anyMatch(a -> a.resolvedAt() == null || a.resolvedAt().isAfter(since));
        return sosOpen || tripAlertOpen;
    }

    /**
     * An account in scope through a given trip (she is its rider or partner
     * and the trip is in scope), or through an SOS she raised herself with no
     * trip at all.
     */
    boolean accountInScope(UUID accountId, UUID bookingId) {
        if (bookingId != null) {
            return bookingInScope(bookingId) && bookings.getParticipants(bookingId).isSuccess()
                    && (accountId.equals(bookings.getParticipants(bookingId).value().customerId())
                    || accountId.equals(bookings.getParticipants(bookingId).value().driverId()));
        }
        Instant since = Instant.now().minus(grace);
        return sos.findRecentForAccount(accountId).stream().anyMatch(a -> live(a, since));
    }

    private static boolean live(SosAlertSummary alert, Instant since) {
        return alert.status() == SosStatus.ACTIVE || (alert.resolvedAt() != null && alert.resolvedAt().isAfter(since));
    }
}
