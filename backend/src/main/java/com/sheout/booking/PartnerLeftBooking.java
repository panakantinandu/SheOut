package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * A partner dropped a trip she had taken, and the booking went back to
 * searching for somebody else - not cancelled: the rider still needs her
 * ride, and a promotion held for it stays held. The search itself is
 * BookingRequested, published alongside, with this partner excluded.
 */
public class PartnerLeftBooking extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;
    private final CancellationReason reason;

    public PartnerLeftBooking(UUID bookingId, UUID customerId, UUID driverId, CancellationReason reason) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.reason = reason;
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

    public CancellationReason reason() {
        return reason;
    }
}
