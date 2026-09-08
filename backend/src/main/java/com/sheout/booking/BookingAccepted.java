package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/** Published when the assigned driver confirms (MATCHED -&gt; ACCEPTED). */
public class BookingAccepted extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;

    public BookingAccepted(UUID bookingId, UUID customerId, UUID driverId) {
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
