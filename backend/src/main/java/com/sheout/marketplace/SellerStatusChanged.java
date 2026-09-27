package com.sheout.marketplace;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A person decided on her shop, or her fee was paid - approved (and so owing
 * the listing fee), rejected, live, suspended or reinstated. Notifications
 * tells her; the reason itself stays in the app, never on a lock screen.
 */
public class SellerStatusChanged extends DomainEvent {

    private final UUID sellerId;
    private final UUID accountId;
    private final SellerStatus status;
    private final BigDecimal listingFeeAmount;

    public SellerStatusChanged(UUID sellerId, UUID accountId, SellerStatus status, BigDecimal listingFeeAmount) {
        this.sellerId = sellerId;
        this.accountId = accountId;
        this.status = status;
        this.listingFeeAmount = listingFeeAmount;
    }

    public UUID sellerId() { return sellerId; }
    public UUID accountId() { return accountId; }
    public SellerStatus status() { return status; }
    /** What she is asked to pay; set when she is approved. */
    public BigDecimal listingFeeAmount() { return listingFeeAmount; }
}
