package com.sheout.campaigns;

import com.sheout.sharedkernel.event.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A partner earned an incentive on a trip. Payouts credits her wallet from
 * this, in the same transaction the award is recorded in.
 */
public class DriverIncentiveAwarded extends DomainEvent {

    private final UUID awardId;
    private final UUID driverId;
    private final UUID bookingId;
    private final BigDecimal amount;
    private final String incentiveName;

    public DriverIncentiveAwarded(UUID awardId, UUID driverId, UUID bookingId, BigDecimal amount, String incentiveName) {
        this.awardId = awardId;
        this.driverId = driverId;
        this.bookingId = bookingId;
        this.amount = amount;
        this.incentiveName = incentiveName;
    }

    public UUID awardId() { return awardId; }
    public UUID driverId() { return driverId; }
    public UUID bookingId() { return bookingId; }
    public BigDecimal amount() { return amount; }
    public String incentiveName() { return incentiveName; }
}
