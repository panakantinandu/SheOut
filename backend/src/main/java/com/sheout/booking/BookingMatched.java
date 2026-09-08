package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * ADDED, NOT EXPLICITLY REQUESTED: the spec named BookingRequested/
 * BookingAccepted/BookingCompleted/BookingCancelled but also said "emit
 * domain events at each state transition" - REQUESTED-&gt;MATCHED is a
 * transition with no named event, and notifications plausibly wants to
 * tell a customer "driver found" at this point (the mockup's "On the Way"
 * screen implies exactly this moment). Added to honor the general rule
 * literally; drop it if it wasn't meant to be included.
 */
public class BookingMatched extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;

    public BookingMatched(UUID bookingId, UUID customerId, UUID driverId) {
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
