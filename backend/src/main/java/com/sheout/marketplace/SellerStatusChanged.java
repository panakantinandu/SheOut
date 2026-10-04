package com.sheout.marketplace;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A person decided on her shop, or her month was paid - approved (and so
 * live on its free month), rejected, live again, suspended, reinstated or
 * lapsed. Notifications tells her; the reason itself stays in the app, never
 * on a lock screen.
 */
public class SellerStatusChanged extends DomainEvent {

    private final UUID sellerId;
    private final UUID accountId;
    private final SellerStatus status;
    private final BigDecimal listingFeeAmount;
    private final Instant paidUntil;
    private final boolean freeMonth;

    public SellerStatusChanged(UUID sellerId, UUID accountId, SellerStatus status, BigDecimal listingFeeAmount,
                               Instant paidUntil, boolean freeMonth) {
        this.paidUntil = paidUntil;
        this.freeMonth = freeMonth;
        this.sellerId = sellerId;
        this.accountId = accountId;
        this.status = status;
        this.listingFeeAmount = listingFeeAmount;
    }

    public UUID sellerId() { return sellerId; }
    public UUID accountId() { return accountId; }
    public SellerStatus status() { return status; }
    /** Her monthly fee. */
    public BigDecimal listingFeeAmount() { return listingFeeAmount; }
    /** Listed until (before grace); null before approval. */
    public Instant paidUntil() { return paidUntil; }
    /** ACTIVE because a person approved her: the month that starts is free. */
    public boolean freeMonth() { return freeMonth; }
}
