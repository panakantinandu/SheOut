package com.sheout.payments;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A seller's listing fee reached CAPTURED - by Checkout or by webhook,
 * whichever got there first. Published once, inside the capturing
 * transaction, exactly as PaymentCaptured is for a trip. PaymentCaptured is
 * not published for a listing fee: everything that listens to it (the
 * partner's wallet, referrals, the trip receipt) is about a trip.
 */
public class ListingFeePaid extends DomainEvent {

    private final UUID paymentId;
    private final UUID payerAccountId;
    private final UUID sellerId;
    private final BigDecimal amount;
    private final PaymentMethod method;
    private final Instant capturedAt;

    public ListingFeePaid(UUID paymentId, UUID payerAccountId, UUID sellerId, BigDecimal amount, PaymentMethod method,
                          Instant capturedAt) {
        this.paymentId = paymentId;
        this.payerAccountId = payerAccountId;
        this.sellerId = sellerId;
        this.amount = amount;
        this.method = method;
        this.capturedAt = capturedAt;
    }

    public UUID paymentId() { return paymentId; }
    public UUID payerAccountId() { return payerAccountId; }
    public UUID sellerId() { return sellerId; }
    public BigDecimal amount() { return amount; }
    public PaymentMethod method() { return method; }
    public Instant capturedAt() { return capturedAt; }
}
