package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * Published on any pre-IN_PROGRESS state -&gt; CANCELLED. {@code driverId}
 * is null if cancelled before a driver was ever assigned.
 * <p>
 * ASSUMPTION FLAGGED: no "cancelled by" / reason field - not specified,
 * and a real system would likely want one (e.g. differing cancellation-fee
 * logic for customer- vs driver-initiated cancellation). Kept minimal
 * rather than guessing at a shape for it.
 */
public class BookingCancelled extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;

    public BookingCancelled(UUID bookingId, UUID customerId, UUID driverId) {
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
