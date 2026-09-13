package com.sheout.booking;

import java.util.UUID;

/**
 * Just enough for another module to check "is this account allowed to act
 * on this booking, and is the booking in a state where that act makes
 * sense" - deliberately not the full BookingSummary, which exposes far more
 * (pickup/drop/fare/timestamps) than an authorization check needs.
 * driverId is null before a driver is assigned.
 * <p>
 * status is here because the checks that need to know who is on a booking
 * almost always need to know what state it is in too. Chat is the clearest
 * case: being a participant decides whether you may READ the thread, and
 * the status decides whether you may still WRITE to it.
 */
public record BookingParticipants(UUID customerId, UUID driverId, BookingStatus status) {
}
