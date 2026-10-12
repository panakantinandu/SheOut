package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/** A delivery is under dispute: the partner's earnings for it stay held, and its photos are kept, until it is closed. */
public class ParcelDisputeOpened extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;

    public ParcelDisputeOpened(UUID bookingId, UUID customerId, UUID driverId) {
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
