package com.sheout.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What an operator needs about one trip beyond BookingSummary: how it ended
 * and who ended it, where the drop-off really happened, the distance quoted
 * against the distance driven, and whether the pickup code was checked.
 * <p>
 * Its own record rather than more fields on BookingSummary, which every
 * module and both apps already read - these are for the console's trip
 * page and nobody else. The pickup code itself is deliberately not here.
 */
public record BookingOpsFacts(
        UUID bookingId,
        CancellationReason cancellationReason,
        String cancellationNote,
        UUID cancelledBy,
        TripEndedBy completedBy,
        Integer completionDistanceFromDropM,
        DropOffDeviationReason dropDeviationReason,
        String dropDeviationNote,
        BigDecimal quotedDistanceKm,
        Boolean quotedDistanceRouted,
        BigDecimal actualDistanceKm,
        Instant routeFlaggedAt,
        Instant routeReviewedAt,
        String routeReviewNote,
        Instant pickupVerifiedAt,
        int pickupAttempts,
        Instant destinationChangedAt
) {
}
