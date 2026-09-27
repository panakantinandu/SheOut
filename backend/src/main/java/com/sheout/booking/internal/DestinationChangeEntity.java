package com.sheout.booking.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One request to change a trip's drop, and its answer. Kept whatever the
 * answer was - it is the record of what both sides were shown and agreed to.
 * See V37 and DestinationChangeService.
 */
@Entity
@Table(name = "booking_destination_changes")
public class DestinationChangeEntity extends BaseEntity {

    public enum Status {
        /** Waiting for the partner. Read through effectiveStatus: a stored PENDING may have run out. */
        PENDING,
        ACCEPTED,
        DECLINED,
        /** Not answered in time, or the trip ended first. Nothing changed. */
        EXPIRED
    }

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "old_drop_label", length = 255)),
            @AttributeOverride(name = "lat", column = @Column(name = "old_drop_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "old_drop_lng"))
    })
    private GeoAddressEmbeddable oldDrop;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "new_drop_label", length = 255)),
            @AttributeOverride(name = "lat", column = @Column(name = "new_drop_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "new_drop_lng"))
    })
    private GeoAddressEmbeddable newDrop;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal oldFare;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal newFare;

    @Column(precision = 8, scale = 2)
    private BigDecimal oldDistanceKm;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal newDistanceKm;

    @Column(nullable = false)
    private boolean newDistanceRouted;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant answeredAt;

    protected DestinationChangeEntity() {
        // JPA
    }

    DestinationChangeEntity(UUID bookingId, UUID requestedBy, GeoAddressEmbeddable oldDrop, GeoAddressEmbeddable newDrop,
                            BigDecimal oldFare, BigDecimal newFare, BigDecimal oldDistanceKm, BigDecimal newDistanceKm,
                            boolean newDistanceRouted, Instant expiresAt) {
        this.bookingId = bookingId;
        this.requestedBy = requestedBy;
        this.status = Status.PENDING;
        this.oldDrop = oldDrop;
        this.newDrop = newDrop;
        this.oldFare = oldFare;
        this.newFare = newFare;
        this.oldDistanceKm = oldDistanceKm;
        this.newDistanceKm = newDistanceKm;
        this.newDistanceRouted = newDistanceRouted;
        this.expiresAt = expiresAt;
    }

    /**
     * What this request is now. A PENDING row past its expiry has lapsed even
     * though nothing has written that down yet - nothing sweeps these, because
     * the only moments it matters are when somebody reads or answers one.
     */
    Status effectiveStatus(Instant now) {
        return status == Status.PENDING && !now.isBefore(expiresAt) ? Status.EXPIRED : status;
    }

    void answer(Status answer, Instant at) {
        this.status = answer;
        this.answeredAt = at;
    }

    /** Settles a lapsed PENDING row so a new request can be written. Answered rows are left alone. */
    void lapse(Instant at) {
        if (status == Status.PENDING) {
            this.status = Status.EXPIRED;
            this.answeredAt = at;
        }
    }

    public UUID getBookingId() { return bookingId; }
    public UUID getRequestedBy() { return requestedBy; }
    public Status getStatus() { return status; }
    public GeoAddressEmbeddable getOldDrop() { return oldDrop; }
    public GeoAddressEmbeddable getNewDrop() { return newDrop; }
    public BigDecimal getOldFare() { return oldFare; }
    public BigDecimal getNewFare() { return newFare; }
    public BigDecimal getOldDistanceKm() { return oldDistanceKm; }
    public BigDecimal getNewDistanceKm() { return newDistanceKm; }
    public boolean isNewDistanceRouted() { return newDistanceRouted; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAnsweredAt() { return answeredAt; }
}
