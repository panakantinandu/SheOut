package com.sheout.booking;

import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/** A parcel dispute was closed: payouts releases the partner's held earnings, or withholds them. */
public class ParcelDisputeClosed extends DomainEvent {

    private final UUID bookingId;
    private final UUID driverId;
    private final ParcelDisputeOutcome outcome;
    private final UUID decidedBy;
    private final String note;

    public ParcelDisputeClosed(UUID bookingId, UUID driverId, ParcelDisputeOutcome outcome, UUID decidedBy, String note) {
        this.bookingId = bookingId;
        this.driverId = driverId;
        this.outcome = outcome;
        this.decidedBy = decidedBy;
        this.note = note;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public UUID driverId() {
        return driverId;
    }

    public ParcelDisputeOutcome outcome() {
        return outcome;
    }

    public UUID decidedBy() {
        return decidedBy;
    }

    public String note() {
        return note;
    }
}
