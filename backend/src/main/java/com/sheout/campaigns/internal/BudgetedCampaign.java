package com.sheout.campaigns.internal;

import com.sheout.campaigns.CampaignStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What a rider promotion and a partner incentive share: dates, a pause, and
 * a budget that stops the campaign by itself.
 * <p>
 * THE BUDGET IS THE SAFETY MECHANISM. Every rupee a campaign pays out is
 * added to spent under a row lock, and a campaign that reaches its cap stops
 * applying to anything new at that moment - automatically, not when somebody
 * notices. What it already paid is not undone. Money given back (a trip
 * cancelled after its discount was held) comes off spent, and a campaign
 * stopped only by its budget resumes if that brings it back under.
 */
@MappedSuperclass
abstract class BudgetedCampaign extends BaseEntity {

    @Column(nullable = false, length = 120)
    protected String name;
    @Column(nullable = false, precision = 10, scale = 2)
    protected BigDecimal value;
    @Column(nullable = false)
    protected Instant validFrom;
    protected Instant validUntil;
    @Column(nullable = false, precision = 12, scale = 2)
    protected BigDecimal budgetCap;
    @Column(nullable = false, precision = 12, scale = 2)
    protected BigDecimal spent = BigDecimal.ZERO;
    @Column(nullable = false)
    protected boolean paused;
    protected Instant autoDisabledAt;

    CampaignStatus statusAt(Instant now) {
        if (paused) return CampaignStatus.PAUSED;
        if (autoDisabledAt != null || remaining().signum() <= 0) return CampaignStatus.BUDGET_REACHED;
        if (now.isBefore(validFrom)) return CampaignStatus.SCHEDULED;
        if (validUntil != null && !now.isBefore(validUntil)) return CampaignStatus.ENDED;
        return CampaignStatus.ACTIVE;
    }

    boolean activeAt(Instant now) {
        return statusAt(now) == CampaignStatus.ACTIVE;
    }

    BigDecimal remaining() {
        return budgetCap.subtract(spent).max(BigDecimal.ZERO);
    }

    /** Never more than what is left: the last award of a campaign is cut to fit, and the campaign stops. */
    BigDecimal affordable(BigDecimal wanted) {
        return wanted.min(remaining()).max(BigDecimal.ZERO);
    }

    void spend(BigDecimal amount, Instant now) {
        spent = spent.add(amount);
        if (spent.compareTo(budgetCap) >= 0 && autoDisabledAt == null) {
            autoDisabledAt = now;
        }
    }

    void giveBack(BigDecimal amount) {
        spent = spent.subtract(amount).max(BigDecimal.ZERO);
        if (autoDisabledAt != null && spent.compareTo(budgetCap) < 0) {
            autoDisabledAt = null;
        }
    }

    void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** A new cap above what is spent lifts a budget stop; one at or below it stops the campaign now. */
    protected void applyCommon(String name, BigDecimal value, Instant validFrom, Instant validUntil,
                               BigDecimal budgetCap, Instant now) {
        this.name = name;
        this.value = value;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.budgetCap = budgetCap;
        if (spent.compareTo(budgetCap) >= 0) {
            if (autoDisabledAt == null) autoDisabledAt = now;
        } else {
            autoDisabledAt = null;
        }
    }

    String getName() { return name; }
    BigDecimal getValue() { return value; }
    Instant getValidFrom() { return validFrom; }
    Instant getValidUntil() { return validUntil; }
    BigDecimal getBudgetCap() { return budgetCap; }
    BigDecimal getSpent() { return spent; }
}
