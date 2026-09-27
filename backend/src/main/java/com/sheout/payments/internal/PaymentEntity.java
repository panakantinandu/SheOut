package com.sheout.payments.internal;

import com.sheout.payments.PaymentMethod;
import com.sheout.payments.PaymentPurpose;
import com.sheout.payments.PaymentStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per booking - bookingId is unique (see V5 migration) so this
 * table is the idempotency backstop: BookingCompletedListener does a
 * find-then-create, but a concurrent duplicate delivery of BookingCompleted
 * (or a retried one) still can't produce two rows for the same booking,
 * since the second insert violates this constraint. No JPA relationship to
 * bookings - plain UUID column, same convention as every other module's
 * cross-module IDs.
 */
@Entity
@Table(name = "payments")
public class PaymentEntity extends BaseEntity {

    /** The trip this pays for. Null for a seller's listing fee, which is no trip's. */
    @Column(unique = true)
    private UUID bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentPurpose purpose = PaymentPurpose.RIDE_FARE;

    /**
     * Who pays a listing fee, and the seller profile it is for. Both null for
     * a trip fare, whose payer is the rider on its booking.
     */
    private UUID payerAccountId;

    @Column(unique = true)
    private UUID sellerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "razorpay_order_id", unique = true)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /**
     * What the partner receives. Null until capture - nothing is owed to
     * anyone before the rider has paid.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal driverPayout;

    /**
     * The rate in force when this payment was captured, frozen onto the row
     * so a later rate change cannot rewrite what somebody was already paid.
     */
    @Column(precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    private Instant capturedAt;

    /**
     * The partner's UPI QR for this fare, while there is one: Razorpay's id,
     * the image to show, and when it stops accepting payments. One live QR
     * at a time; an expired one is replaced when she asks again.
     */
    @Column(name = "razorpay_qr_id", unique = true, length = 64)
    private String razorpayQrId;

    @Column(name = "qr_image_url", length = 500)
    private String qrImageUrl;

    @Column(name = "qr_expires_at")
    private Instant qrExpiresAt;

    protected PaymentEntity() {
        // JPA
    }

    public PaymentEntity(UUID bookingId, BigDecimal amount, PaymentMethod method, PaymentStatus status) {
        this.bookingId = bookingId;
        this.amount = amount;
        this.method = method;
        this.status = status;
    }

    /** A seller's listing fee: no booking, nobody to settle a share with. */
    public static PaymentEntity listingFee(UUID payerAccountId, UUID sellerId, BigDecimal amount) {
        PaymentEntity payment = new PaymentEntity(null, amount, PaymentMethod.UPI, PaymentStatus.PENDING);
        payment.purpose = PaymentPurpose.SELLER_LISTING_FEE;
        payment.payerAccountId = payerAccountId;
        payment.sellerId = sellerId;
        payment.fareAmount = amount;
        return payment;
    }

    public String getRazorpayQrId() {
        return razorpayQrId;
    }

    public String getQrImageUrl() {
        return qrImageUrl;
    }

    public Instant getQrExpiresAt() {
        return qrExpiresAt;
    }

    public void setQr(String razorpayQrId, String qrImageUrl, Instant qrExpiresAt) {
        this.razorpayQrId = razorpayQrId;
        this.qrImageUrl = qrImageUrl;
        this.qrExpiresAt = qrExpiresAt;
    }

    public PaymentPurpose getPurpose() {
        return purpose;
    }

    public UUID getPayerAccountId() {
        return payerAccountId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * The whole fare, when a promotion paid part of it - amount is then what
     * the rider was charged. Null on payments from before promotions, where
     * amount was the whole fare.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal fareAmount;

    public void setFareAmount(BigDecimal fareAmount) {
        this.fareAmount = fareAmount;
    }

    /** What the partner's share is worked out from: always the whole fare. */
    public BigDecimal getFareAmount() {
        return fareAmount != null ? fareAmount : amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public void setRazorpayOrderId(String razorpayOrderId) {
        this.razorpayOrderId = razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public void setRazorpayPaymentId(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public BigDecimal getDriverPayout() {
        return driverPayout;
    }

    public BigDecimal getCommissionPercent() {
        return commissionPercent;
    }

    /** Set together, because a payout without the rate that produced it cannot be explained to anybody. */
    public void recordSettlement(BigDecimal driverPayout, BigDecimal commissionPercent) {
        this.driverPayout = driverPayout;
        this.commissionPercent = commissionPercent;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }
}
