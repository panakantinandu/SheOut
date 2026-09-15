package com.sheout.payouts;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An operator has recorded a payout request as paid. Published inside the
 * transaction that marks it, so a listener reacting after commit only ever
 * hears about a payout that really is recorded as paid.
 */
public class PayoutMarkedPaid extends DomainEvent {

    private final UUID requestId;
    private final UUID driverAccountId;
    private final BigDecimal amount;
    private final String paymentReference;

    public PayoutMarkedPaid(UUID requestId, UUID driverAccountId, BigDecimal amount, String paymentReference) {
        this.requestId = requestId;
        this.driverAccountId = driverAccountId;
        this.amount = amount;
        this.paymentReference = paymentReference;
    }

    public UUID requestId() {
        return requestId;
    }

    public UUID driverAccountId() {
        return driverAccountId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String paymentReference() {
        return paymentReference;
    }
}
