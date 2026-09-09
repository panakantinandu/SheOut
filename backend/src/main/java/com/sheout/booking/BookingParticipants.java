package com.sheout.booking;

import java.util.UUID;

/**
 * Just enough for another module to check "is this account allowed to act
 * on this booking" - deliberately not the full BookingSummary, which
 * exposes far more (pickup/drop/fare/status/timestamps) than an
 * authorization check needs. driverId is null before a driver is assigned.
 */
public record BookingParticipants(UUID customerId, UUID driverId) {
}
