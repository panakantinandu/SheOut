package com.sheout.insurance.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** A claim (or accident report) raised on a trip: the support ticket, beside the coverage it is about. */
@Entity
@Table(name = "trip_coverage_claims")
public class TripCoverageClaimEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID bookingId;

    /** Null when the trip had no cover - an accident is still reported, there is just no policy to claim on. */
    private UUID coverageId;

    @Column(nullable = false)
    private UUID ticketId;

    @Column(nullable = false)
    private UUID raisedBy;

    protected TripCoverageClaimEntity() {
        // JPA
    }

    TripCoverageClaimEntity(UUID bookingId, UUID coverageId, UUID ticketId, UUID raisedBy) {
        this.bookingId = bookingId;
        this.coverageId = coverageId;
        this.ticketId = ticketId;
        this.raisedBy = raisedBy;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getCoverageId() {
        return coverageId;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getRaisedBy() {
        return raisedBy;
    }
}
