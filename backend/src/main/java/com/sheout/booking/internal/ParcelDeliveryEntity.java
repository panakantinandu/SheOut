package com.sheout.booking.internal;

import com.sheout.booking.DropOffDeviationReason;
import com.sheout.booking.ParcelDisputeOutcome;
import com.sheout.booking.ParcelExceptionReason;
import com.sheout.booking.ParcelSenderChoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A delivery's proof-of-delivery record - see V80 for what each column
 * holds. One per delivery booked since proof of delivery existed; keyed by
 * the booking, so there can never be two. Changed only through the methods
 * below, which ParcelDeliveryService calls under the booking's row lock.
 */
@Entity
@Table(name = "parcel_deliveries")
public class ParcelDeliveryEntity {

    /** What "drop check" recorded when the parcel was marked delivered. */
    enum DropCheck { AT_DROP, OUTSIDE, UNAVAILABLE }

    @Id
    private UUID bookingId;

    @Column(nullable = false, length = 80)
    private String receiverName;
    @Column(length = 16)
    private String receiverPhone;
    private Instant receiverPhoneErasedAt;
    @Column(precision = 10, scale = 2)
    private BigDecimal declaredValue;
    @Column(nullable = false, length = 20)
    private String prohibitedAckVersion;
    @Column(nullable = false)
    private boolean sizeAck;
    @Column(nullable = false)
    private Instant acknowledgedAt;

    @Column(length = 4)
    private String deliveryCode;
    @Column(nullable = false)
    private int deliveryCodeAttempts;
    private Instant deliveryVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DropCheck dropCheck;
    @Column(name = "drop_check_distance_m")
    private Integer dropCheckDistanceM;
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private DropOffDeviationReason outsideReason;
    @Column(length = 500)
    private String outsideNote;
    private Instant reviewFlaggedAt;
    @Column(length = 40)
    private String reviewFlagReason;
    private Instant reviewedAt;
    private UUID reviewedBy;
    @Column(length = 1000)
    private String reviewNote;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ParcelExceptionReason exceptionReason;
    @Column(length = 500)
    private String exceptionNote;
    private Instant exceptionAt;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ParcelSenderChoice senderChoice;
    private Instant senderChoiceAt;
    @Column(length = 80)
    private String leaveWithName;
    @Column(precision = 10, scale = 2)
    private BigDecimal returnFare;
    private Instant returnFareWaivedAt;
    private UUID returnFareWaivedBy;

    private Instant disputeOpenedAt;
    private UUID disputeOpenedBy;
    @Column(length = 1000)
    private String disputeNote;
    private Instant disputeClosedAt;
    private UUID disputeClosedBy;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ParcelDisputeOutcome disputeOutcome;
    @Column(length = 1000)
    private String disputeResolutionNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected ParcelDeliveryEntity() {
        // JPA
    }

    ParcelDeliveryEntity(UUID bookingId, String receiverName, String receiverPhone, BigDecimal declaredValue,
                         String prohibitedAckVersion, boolean sizeAck, Instant acknowledgedAt) {
        this.bookingId = bookingId;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.declaredValue = declaredValue;
        this.prohibitedAckVersion = prohibitedAckVersion;
        this.sizeAck = sizeAck;
        this.acknowledgedAt = acknowledgedAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---- the delivery code ----

    void issueCode(String code) {
        this.deliveryCode = code;
    }

    void recordFailedCodeAttempt() {
        deliveryCodeAttempts++;
    }

    boolean codeLocked() {
        return deliveryCodeAttempts >= PickupCode.MAX_ATTEMPTS;
    }

    void recordDelivered(Instant at, boolean byCode, DropCheck check, Integer distanceM,
                         DropOffDeviationReason reason, String note) {
        if (byCode) {
            this.deliveryVerifiedAt = at;
        }
        this.dropCheck = check;
        this.dropCheckDistanceM = distanceM;
        this.outsideReason = check == DropCheck.AT_DROP ? null : reason;
        this.outsideNote = check == DropCheck.AT_DROP ? null : note;
    }

    /** Never silent: anything an operator should look at is flagged, with why. The first reason wins. */
    void flagForReview(String reason, Instant at) {
        if (reviewFlaggedAt == null) {
            reviewFlaggedAt = at;
            reviewFlagReason = reason;
        }
    }

    void recordReview(UUID by, String note, Instant at) {
        reviewedAt = at;
        reviewedBy = by;
        reviewNote = note;
    }

    // ---- receiver not there ----

    /** A fresh exception asks the sender again, even after she chose to wait. */
    void raiseException(ParcelExceptionReason reason, String note, Instant at) {
        exceptionReason = reason;
        exceptionNote = note;
        exceptionAt = at;
        senderChoice = null;
        senderChoiceAt = null;
        leaveWithName = null;
    }

    boolean awaitingSenderChoice() {
        return exceptionAt != null && senderChoice == null;
    }

    void recordSenderChoice(ParcelSenderChoice choice, String leaveWith, BigDecimal returnFare, Instant at) {
        senderChoice = choice;
        senderChoiceAt = at;
        leaveWithName = choice == ParcelSenderChoice.LEAVE_WITH ? leaveWith : null;
        if (choice == ParcelSenderChoice.RETURN) {
            this.returnFare = returnFare;
        }
    }

    void waiveReturnFare(UUID by, Instant at) {
        returnFareWaivedAt = at;
        returnFareWaivedBy = by;
    }

    // ---- dispute ----

    boolean disputeOpen() {
        return disputeOpenedAt != null && disputeClosedAt == null;
    }

    void openDispute(UUID by, String note, Instant at) {
        disputeOpenedAt = at;
        disputeOpenedBy = by;
        disputeNote = note;
        disputeClosedAt = null;
        disputeClosedBy = null;
        disputeOutcome = null;
        disputeResolutionNote = null;
    }

    void closeDispute(UUID by, ParcelDisputeOutcome outcome, String note, Instant at) {
        disputeClosedAt = at;
        disputeClosedBy = by;
        disputeOutcome = outcome;
        disputeResolutionNote = note;
    }

    void eraseReceiverPhone(Instant at) {
        receiverPhone = null;
        receiverPhoneErasedAt = at;
    }

    public UUID getBookingId() { return bookingId; }
    public String getReceiverName() { return receiverName; }
    public String getReceiverPhone() { return receiverPhone; }
    public BigDecimal getDeclaredValue() { return declaredValue; }
    public String getProhibitedAckVersion() { return prohibitedAckVersion; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public String getDeliveryCode() { return deliveryCode; }
    public int getDeliveryCodeAttempts() { return deliveryCodeAttempts; }
    public Instant getDeliveryVerifiedAt() { return deliveryVerifiedAt; }
    public DropCheck getDropCheck() { return dropCheck; }
    public Integer getDropCheckDistanceM() { return dropCheckDistanceM; }
    public DropOffDeviationReason getOutsideReason() { return outsideReason; }
    public String getOutsideNote() { return outsideNote; }
    public Instant getReviewFlaggedAt() { return reviewFlaggedAt; }
    public String getReviewFlagReason() { return reviewFlagReason; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public ParcelExceptionReason getExceptionReason() { return exceptionReason; }
    public String getExceptionNote() { return exceptionNote; }
    public Instant getExceptionAt() { return exceptionAt; }
    public ParcelSenderChoice getSenderChoice() { return senderChoice; }
    public Instant getSenderChoiceAt() { return senderChoiceAt; }
    public String getLeaveWithName() { return leaveWithName; }
    public BigDecimal getReturnFare() { return returnFare; }
    public Instant getReturnFareWaivedAt() { return returnFareWaivedAt; }
    public Instant getDisputeOpenedAt() { return disputeOpenedAt; }
    public String getDisputeNote() { return disputeNote; }
    public Instant getDisputeClosedAt() { return disputeClosedAt; }
    public ParcelDisputeOutcome getDisputeOutcome() { return disputeOutcome; }
    public String getDisputeResolutionNote() { return disputeResolutionNote; }
    public Instant getCreatedAt() { return createdAt; }
}
