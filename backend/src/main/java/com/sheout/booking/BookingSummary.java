package com.sheout.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a booking. Deliberately carries only IDs for
 * customer/driver, not composed names/phone numbers (unlike users'
 * CustomerProfileSummary/DriverProfileSummary) - booking doesn't depend on
 * the users module at all; a caller wanting a driver's name alongside this
 * would call users' DriverProfileApi itself.
 */
public record BookingSummary(
        UUID id,
        BookingType type,
        BookingCategory category,
        BookingStatus status,
        UUID customerId,
        UUID driverId,
        GeoAddress pickup,
        GeoAddress drop,
        BigDecimal fareEstimate,
        BigDecimal finalFare,
        Instant requestedAt,
        Instant matchedAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        /**
         * When the rider's payment was captured. A COMPLETED booking with
         * this null has ended but is not paid, and is shown that way in both
         * apps - see paymentPending().
         */
        Instant paymentSettledAt,
        /** What a promotion paid towards the fare; zero when none. */
        BigDecimal promoDiscount,
        /** What the rider is charged: the fare less the promotion. */
        BigDecimal amountDue,
        /** Which promotion, by its name - null when none. */
        String promotionName
) {

    /** Ended by the partner, not yet paid for. */
    public boolean paymentPending() {
        return status == BookingStatus.COMPLETED && paymentSettledAt == null;
    }
}
