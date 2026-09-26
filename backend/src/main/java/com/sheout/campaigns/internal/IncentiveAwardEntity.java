package com.sheout.campaigns.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** One incentive paid on one trip. Unique per (incentive, trip), so a replayed event cannot pay twice. */
@Entity
@Table(name = "incentive_awards")
public class IncentiveAwardEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID incentiveId;
    @Column(nullable = false)
    private UUID driverId;
    @Column(nullable = false)
    private UUID bookingId;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    protected IncentiveAwardEntity() {
    }

    IncentiveAwardEntity(UUID incentiveId, UUID driverId, UUID bookingId, BigDecimal amount) {
        this.incentiveId = incentiveId;
        this.driverId = driverId;
        this.bookingId = bookingId;
        this.amount = amount;
    }

    UUID getIncentiveId() { return incentiveId; }
    UUID getDriverId() { return driverId; }
    BigDecimal getAmount() { return amount; }
}
