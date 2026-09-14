package com.sheout.payments;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A booking's payment reached CAPTURED - by Razorpay Checkout, by webhook, or
 * by the partner confirming cash. Published once per payment: capture is
 * terminal and every path checks the status under a row lock first.
 * <p>
 * Published inside the capturing transaction, so a subscriber using a plain
 * @EventListener commits or rolls back with the capture itself - a wallet
 * cannot be credited for a capture that did not happen, or miss one that did.
 * <p>
 * Carries no driver id: payments knows bookings, not who drove them. A
 * subscriber that needs the partner asks booking.
 */
public class PaymentCaptured extends DomainEvent {

    private final UUID paymentId;
    private final UUID bookingId;
    private final PaymentMethod method;
    private final BigDecimal amount;
    private final BigDecimal driverPayout;
    private final BigDecimal commissionPercent;
    private final Instant capturedAt;

    public PaymentCaptured(UUID paymentId, UUID bookingId, PaymentMethod method, BigDecimal amount,
                           BigDecimal driverPayout, BigDecimal commissionPercent, Instant capturedAt) {
        this.paymentId = paymentId;
        this.bookingId = bookingId;
        this.method = method;
        this.amount = amount;
        this.driverPayout = driverPayout;
        this.commissionPercent = commissionPercent;
        this.capturedAt = capturedAt;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public UUID bookingId() {
        return bookingId;
    }

    public PaymentMethod method() {
        return method;
    }

    /** What the rider paid. */
    public BigDecimal amount() {
        return amount;
    }

    /** The partner's share of amount, after commission. */
    public BigDecimal driverPayout() {
        return driverPayout;
    }

    public BigDecimal commissionPercent() {
        return commissionPercent;
    }

    public Instant capturedAt() {
        return capturedAt;
    }
}
