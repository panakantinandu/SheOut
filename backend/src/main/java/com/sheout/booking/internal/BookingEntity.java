package com.sheout.booking.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.CancellationReason;
import com.sheout.booking.DropOffDeviationReason;
import com.sheout.booking.TripEndedBy;
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

    /**
     * When the rider's payment for this trip was captured. Null on a
     * COMPLETED booking means the partner has ended the trip and it is still
     * unpaid - see V23. Set only by payments, through BookingApi.
     */
    private Instant paymentSettledAt;

    /** All three are null unless this booking was cancelled - see V9. */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private CancellationReason cancellationReason;

    @Column(length = 500)
    private String cancellationNote;

    @Column
    private UUID cancelledBy;

    /**
     * The code the rider reads to her partner at pickup. Set when the
     * partner accepts; null before that, and on bookings accepted before
     * this check existed. Never released to the driver by any endpoint -
     * see BookingController's pickup-code endpoint.
     */
    @Column(length = 4)
    private String pickupOtp;

    /** When the partner proved she was at the pickup. Null means this trip was never verified. */
    private Instant pickupVerifiedAt;

    /** Wrong guesses so far. See {@link PickupCode#MAX_ATTEMPTS}. */
    @Column(nullable = false)
    private int pickupAttempts;

    // What was quoted, how it ended, and the route check - see
    // V35__drop_off_and_route_check.sql for what each column records.
    @Column(precision = 8, scale = 2)
    private BigDecimal quotedDistanceKm;
    private Boolean quotedDistanceRouted;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TripEndedBy completedBy;
    private Double completionLat;
    private Double completionLng;
    @Column(name = "completion_distance_from_drop_m")
    private Integer completionDistanceFromDropM;
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private DropOffDeviationReason dropDeviationReason;
    @Column(length = 500)
    private String dropDeviationNote;

    @Column(precision = 8, scale = 2)
    private BigDecimal actualDistanceKm;
    private Integer routePoints;
    private Instant routeFlaggedAt;
    private Instant routeReviewedAt;
    private UUID routeReviewedBy;
    @Column(length = 1000)
    private String routeReviewNote;

    protected BookingEntity() {
        // JPA
    }

    /** The road distance the fare was priced on, and whether it came from a real route or the fallback estimate. */
    public void recordQuotedDistance(BigDecimal distanceKm, boolean routed) {
        this.quotedDistanceKm = distanceKm;
        this.quotedDistanceRouted = routed;
    }

    /**
     * How the trip ended. The position is the partner's last trusted fix -
     * null when there was none - and the reason is whatever she gave, kept
     * whether or not it was needed.
     */
    public void recordCompletion(TripEndedBy by, Double lat, Double lng, Integer distanceFromDropM,
                                 DropOffDeviationReason reason, String note) {
        this.completedBy = by;
        this.completionLat = lat;
        this.completionLng = lng;
        this.completionDistanceFromDropM = distanceFromDropM;
        this.dropDeviationReason = reason;
        this.dropDeviationNote = note;
    }

    /** The measured route. actualKm is null when the reports were too sparse to measure from; flagged means a person should look. */
    public void recordRouteCheck(BigDecimal actualKm, int points, boolean flagged, Instant at) {
        this.actualDistanceKm = actualKm;
        this.routePoints = points;
        this.routeFlaggedAt = flagged ? at : null;
    }

    /** An operator has looked, and says why it is fine (or what was done). Nothing else changes. */
    public void recordRouteReview(UUID adminAccountId, String note, Instant at) {
        this.routeReviewedAt = at;
        this.routeReviewedBy = adminAccountId;
        this.routeReviewNote = note;
    }

    public BigDecimal getQuotedDistanceKm() { return quotedDistanceKm; }
    public Boolean getQuotedDistanceRouted() { return quotedDistanceRouted; }
    public TripEndedBy getCompletedBy() { return completedBy; }
    public Double getCompletionLat() { return completionLat; }
    public Double getCompletionLng() { return completionLng; }
    public Integer getCompletionDistanceFromDropM() { return completionDistanceFromDropM; }
    public DropOffDeviationReason getDropDeviationReason() { return dropDeviationReason; }
    public String getDropDeviationNote() { return dropDeviationNote; }
    public BigDecimal getActualDistanceKm() { return actualDistanceKm; }
    public Integer getRoutePoints() { return routePoints; }
    public Instant getRouteFlaggedAt() { return routeFlaggedAt; }
    public Instant getRouteReviewedAt() { return routeReviewedAt; }
    public UUID getRouteReviewedBy() { return routeReviewedBy; }
    public String getRouteReviewNote() { return routeReviewNote; }

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

    public Instant getPaymentSettledAt() {
        return paymentSettledAt;
    }

    public void setPaymentSettledAt(Instant paymentSettledAt) {
        this.paymentSettledAt = paymentSettledAt;
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

    public String getPickupOtp() {
        return pickupOtp;
    }

    public void setPickupOtp(String pickupOtp) {
        this.pickupOtp = pickupOtp;
    }

    public Instant getPickupVerifiedAt() {
        return pickupVerifiedAt;
    }

    public void setPickupVerifiedAt(Instant pickupVerifiedAt) {
        this.pickupVerifiedAt = pickupVerifiedAt;
    }

    public int getPickupAttempts() {
        return pickupAttempts;
    }

    public void recordFailedPickupAttempt() {
        this.pickupAttempts++;
    }

    /**
     * Whether this booking has run out of tries.
     * <p>
     * Asked before a guess is checked, so the limit is a real stop rather
     * than something that merely gets recorded after the fact.
     */
    public boolean pickupAttemptsExhausted() {
        return pickupAttempts >= PickupCode.MAX_ATTEMPTS;
    }
}
