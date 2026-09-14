package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything an operator reads to work one ticket, in one response.
 * <p>
 * The linked booking and any SOS alert raised on it come with the ticket
 * rather than as links to go and follow. A safety concern and an SOS on the
 * same trip are one situation, and an operator should not be able to answer
 * the ticket without having seen that the rider pressed SOS.
 */
public record SupportTicketOpsDetail(
        SupportTicketOpsRow ticket,
        List<Message> messages,
        LinkedBooking linkedBooking,
        List<LinkedSosAlert> sosAlerts
) {

    /**
     * authorLabel is the operator's phone for an ADMIN author and the
     * raiser's name otherwise - never shown to the raiser, who sees only
     * "SheOut Support".
     */
    public record Message(UUID id, AccountRole authorRole, String authorLabel, String message,
                          boolean internalOnly, Instant createdAt) {
    }

    public record LinkedBooking(UUID id, BookingStatus status, BookingCategory category, String pickupLabel,
                                String dropLabel, Instant requestedAt, String customerName, String driverName) {
    }

    public record LinkedSosAlert(UUID id, String status, double lat, double lng, int contactsNotified,
                                 Instant createdAt, Instant resolvedAt) {
    }
}
