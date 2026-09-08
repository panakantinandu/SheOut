package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * ADDED, NOT EXPLICITLY REQUESTED - same reasoning as {@link BookingMatched}:
 * ACCEPTED-&gt;IN_PROGRESS is a transition with no named event in the spec,
 * added to honor "emit domain events at each state transition" literally.
 * Drop it if it wasn't meant to be included.
 */
public class BookingStarted extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;

    public BookingStarted(UUID bookingId, UUID customerId, UUID driverId) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID driverId() {
        return driverId;
    }
}
