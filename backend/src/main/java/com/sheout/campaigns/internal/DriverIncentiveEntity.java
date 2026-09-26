package com.sheout.campaigns.internal;

import com.sheout.campaigns.IncentiveType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** A partner incentive. See V36__campaigns.sql, and IncentiveRules for what each type pays. */
@Entity
@Table(name = "driver_incentives")
public class DriverIncentiveEntity extends BudgetedCampaign {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IncentiveType type;
    @Column(name = "first_n_trips")
    private Integer firstNTrips;

    protected DriverIncentiveEntity() {
    }

    DriverIncentiveEntity(IncentiveType type) {
        this.type = type;
    }

    void apply(String name, BigDecimal value, Integer firstNTrips, Instant validFrom, Instant validUntil,
               BigDecimal budgetCap, Instant now) {
        applyCommon(name, value, validFrom, validUntil, budgetCap, now);
        this.firstNTrips = firstNTrips;
    }

    IncentiveType getType() { return type; }
    Integer getFirstNTrips() { return firstNTrips; }
}
