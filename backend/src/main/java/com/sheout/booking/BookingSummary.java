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
        Instant cancelledAt
) {
}
