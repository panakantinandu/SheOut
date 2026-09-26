package com.sheout.campaigns.internal;

import com.sheout.campaigns.PromotionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** A rider promotion. See V36__campaigns.sql for each column and BudgetedCampaign for the budget rule. */
@Entity
@Table(name = "promotions")
public class PromotionEntity extends BudgetedCampaign {

    @Column(length = 40)
    private String code;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PromotionType type;
    @Column(precision = 10, scale = 2)
    private BigDecimal maxDiscountPerBooking;
    @Column(nullable = false)
    private int maxUsesPerAccount = 1;
    private Integer creditValidDays;

    protected PromotionEntity() {
    }

    PromotionEntity(PromotionType type) {
        this.type = type;
    }

    void apply(String name, String code, BigDecimal value, BigDecimal maxDiscountPerBooking, int maxUsesPerAccount,
               Integer creditValidDays, Instant validFrom, Instant validUntil, BigDecimal budgetCap, Instant now) {
        applyCommon(name, value, validFrom, validUntil, budgetCap, now);
        this.code = code;
        this.maxDiscountPerBooking = maxDiscountPerBooking;
        this.maxUsesPerAccount = maxUsesPerAccount;
        this.creditValidDays = creditValidDays;
    }

    /**
     * What this promotion would take off a fare, before the budget. A signup
     * credit's is whatever the rider has left of it.
     */
    BigDecimal discountOn(BigDecimal fare, BigDecimal creditLeft) {
        BigDecimal wanted = switch (type) {
            case SIGNUP_CREDIT -> creditLeft == null ? BigDecimal.ZERO : creditLeft;
            case FLAT_DISCOUNT -> value;
            case PERCENTAGE_DISCOUNT -> {
                BigDecimal pct = fare.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                yield maxDiscountPerBooking == null ? pct : pct.min(maxDiscountPerBooking);
            }
        };
        return wanted.min(fare).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    String getCode() { return code; }
    PromotionType getType() { return type; }
    BigDecimal getMaxDiscountPerBooking() { return maxDiscountPerBooking; }
    int getMaxUsesPerAccount() { return maxUsesPerAccount; }
    Integer getCreditValidDays() { return creditValidDays; }
}
