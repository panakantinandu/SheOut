package com.sheout.marketplace;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A reminder about her monthly plan is due - see the marketplace's
 * SellerPlanReminders. Notifications tells her; nothing else listens.
 * <ul>
 *   <li>ENDS_SOON - her month ends in {@code daysLeft} days.</li>
 *   <li>ENDED - it has ended; her shop stays listed until hiddenFrom.</li>
 *   <li>HIDDEN - it is hidden from customers until she pays.</li>
 * </ul>
 */
public class SellerPlanNotice extends DomainEvent {

    public enum Kind { ENDS_SOON, ENDED, HIDDEN }

    private final UUID sellerId;
    private final UUID accountId;
    private final Kind kind;
    private final int daysLeft;
    private final boolean freeMonth;
    private final Instant paidUntil;
    private final Instant hiddenFrom;
    private final BigDecimal monthlyFee;

    public SellerPlanNotice(UUID sellerId, UUID accountId, Kind kind, int daysLeft, boolean freeMonth,
                            Instant paidUntil, Instant hiddenFrom, BigDecimal monthlyFee) {
        this.sellerId = sellerId;
        this.accountId = accountId;
        this.kind = kind;
        this.daysLeft = daysLeft;
        this.freeMonth = freeMonth;
        this.paidUntil = paidUntil;
        this.hiddenFrom = hiddenFrom;
        this.monthlyFee = monthlyFee;
    }

    public UUID sellerId() { return sellerId; }
    public UUID accountId() { return accountId; }
    public Kind kind() { return kind; }
    /** Whole days until her month ends, for ENDS_SOON. */
    public int daysLeft() { return daysLeft; }
    /** The month that is ending was her free one. */
    public boolean freeMonth() { return freeMonth; }
    public Instant paidUntil() { return paidUntil; }
    public Instant hiddenFrom() { return hiddenFrom; }
    public BigDecimal monthlyFee() { return monthlyFee; }
}
