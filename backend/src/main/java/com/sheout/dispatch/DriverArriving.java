package com.sheout.dispatch;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * The partner on an accepted booking has come within the arriving radius of
 * the pickup. Published at most once per booking, from the partner's own
 * location reports - see DispatchService.recordLocation.
 * <p>
 * There is no ARRIVING booking status and this does not add one: arriving
 * is a fact about where somebody is, not a step a trip has to pass through.
 * It exists so a rider waiting on a pavement is told to look up.
 */
public class DriverArriving extends DomainEvent {

    private final UUID bookingId;
    private final UUID customerId;
    private final UUID driverId;

    public DriverArriving(UUID bookingId, UUID customerId, UUID driverId) {
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
