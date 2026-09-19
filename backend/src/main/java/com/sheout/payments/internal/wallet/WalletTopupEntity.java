package com.sheout.payments.internal.wallet;

import com.sheout.payments.PaymentMethod;
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
 * Money a rider asked to add. Nothing reaches her balance until Razorpay
 * confirms the capture; this row is what ties that confirmation back to her.
 */
@Entity
@Table(name = "wallet_topups")
public class WalletTopupEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID customerAccountId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(unique = true)
    private String razorpayOrderId;

    @Column(unique = true)
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod method;

    @Column(length = 500)
    private String failureReason;

    private Instant capturedAt;

    protected WalletTopupEntity() {
        // JPA
    }

    WalletTopupEntity(UUID customerAccountId, BigDecimal amount) {
        this.customerAccountId = customerAccountId;
        this.amount = amount;
    }

    public UUID getCustomerAccountId() {
        return customerAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    void attachOrder(String orderId) {
        this.razorpayOrderId = orderId;
    }

    void markCaptured(String paymentId, PaymentMethod method) {
        this.status = PaymentStatus.CAPTURED;
        this.razorpayPaymentId = paymentId;
        this.method = method;
        this.failureReason = null;
        this.capturedAt = Instant.now();
    }

    /** Not terminal: Razorpay lets her retry inside the same Checkout, and a later capture still counts. */
    void markFailed(String paymentId, String reason) {
        this.status = PaymentStatus.FAILED;
        if (paymentId != null) {
            this.razorpayPaymentId = paymentId;
        }
        this.failureReason = reason;
    }
}
