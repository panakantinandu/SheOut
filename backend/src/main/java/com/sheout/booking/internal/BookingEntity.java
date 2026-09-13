package com.sheout.booking.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.CancellationReason;
import com.sheout.booking.BookingType;
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
 * customerId/driverId are plain UUID columns, not JPA relationships - no
 * cross-module foreign keys, same convention as every other module.
 * BaseEntity's createdAt doubles as "requestedAt" (the moment REQUESTED
 * happens is exactly entity creation) - not duplicated as a separate column.
 */
@Entity
@Table(name = "bookings")
public class BookingEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingCategory category;

    @Enumerated(EnumType.STRING)
    // 30, not 20: NO_DRIVERS_AVAILABLE is exactly 20 characters, so the
    // old width fit it by coincidence with no room for the next one. See
    // V11.
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(nullable = false)
    private UUID customerId;

    private UUID driverId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "pickup_label", length = 255)),
            @AttributeOverride(name = "lat", column = @Column(name = "pickup_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "pickup_lng"))
    })
    private GeoAddressEmbeddable pickup;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "label", column = @Column(name = "drop_label", length = 255)),
            @AttributeOverride(name = "lat", column = @Column(name = "drop_lat")),
            @AttributeOverride(name = "lng", column = @Column(name = "drop_lng"))
    })
    private GeoAddressEmbeddable drop;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fareEstimate;

    @Column(precision = 10, scale = 2)
    private BigDecimal finalFare;

    private Instant matchedAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant cancelledAt;

    /** All three are null unless this booking was cancelled - see V9. */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private CancellationReason cancellationReason;

    @Column(length = 500)
    private String cancellationNote;

    @Column
    private UUID cancelledBy;

    protected BookingEntity() {
        // JPA
    }

    public BookingEntity(BookingType type, BookingCategory category, UUID customerId,
                          GeoAddressEmbeddable pickup, GeoAddressEmbeddable drop, BigDecimal fareEstimate) {
        this.type = type;
        this.category = category;
        this.status = BookingStatus.REQUESTED;
        this.customerId = customerId;
        this.pickup = pickup;
        this.drop = drop;
        this.fareEstimate = fareEstimate;
    }

    public BookingType getType() {
        return type;
    }

    public BookingCategory getCategory() {
        return category;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
    }

    public GeoAddressEmbeddable getPickup() {
        return pickup;
    }

    public GeoAddressEmbeddable getDrop() {
        return drop;
    }

    public BigDecimal getFareEstimate() {
        return fareEstimate;
    }

    public BigDecimal getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(BigDecimal finalFare) {
        this.finalFare = finalFare;
    }

    public Instant getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(Instant matchedAt) {
        this.matchedAt = matchedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public CancellationReason getCancellationReason() {
        return cancellationReason;
    }

    public String getCancellationNote() {
        return cancellationNote;
    }

    public UUID getCancelledBy() {
        return cancelledBy;
    }

    /** Set together, always, by BookingService.cancelBooking. */
    public void recordCancellation(UUID by, CancellationReason reason, String note) {
        this.cancelledBy = by;
        this.cancellationReason = reason;
        this.cancellationNote = note;
    }
}
