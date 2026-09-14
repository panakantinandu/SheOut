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

    /**
     * Whether this account is the booking's customer or its assigned driver.
     * <p>
     * The one definition of "on this booking", for every module's
     * enumeration-safe check. Callers answer false exactly as they answer a
     * booking that does not exist - one 404, one message - so an id cannot be
     * probed for existence. It used to be written out by hand in chat,
     * payments and booking separately, which is three places for the rule to
     * drift.
     */
    public boolean includes(UUID accountId) {
        return accountId != null && (accountId.equals(customerId) || accountId.equals(driverId));
    }
}
