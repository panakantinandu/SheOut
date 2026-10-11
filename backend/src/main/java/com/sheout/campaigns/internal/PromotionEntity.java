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
     * What this promotion would take off a fare, before the budget, in WHOLE
     * RUPEES: the fare is whole (see DistanceBasedFareCalculator), so what she
     * pays stays whole too, and never below zero.
     * <ul>
     *   <li>A percentage is rounded half up (10% of 107 = 10.70 -> 11), then
     *       held to the per-trip cap - the cap itself rounded down, so the
     *       promised maximum is never exceeded.</li>
     *   <li>A flat amount, or a credit balance, is rounded down: never more
     *       than was granted. A balance left with paise (from fares before
     *       they were whole) keeps them for a later trip.</li>
     * </ul>
     */
    BigDecimal discountOn(BigDecimal fare, BigDecimal creditLeft) {
        BigDecimal wanted = switch (type) {
            case SIGNUP_CREDIT, REFERRAL_REWARD, REFERRAL_WELCOME -> creditLeft == null ? BigDecimal.ZERO : rupeesDown(creditLeft);
            case FLAT_DISCOUNT -> rupeesDown(value);
            case PERCENTAGE_DISCOUNT -> {
                BigDecimal pct = fare.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                yield maxDiscountPerBooking == null ? pct : pct.min(rupeesDown(maxDiscountPerBooking));
            }
        };
        return wanted.min(fare).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The discount this promotion can actually give: discountOn, held to what
     * is left of its budget - and that, too, in whole rupees, rounded down so
     * the budget is never overspent.
     */
    BigDecimal wholeDiscountOn(BigDecimal fare, BigDecimal creditLeft) {
        return rupeesDown(affordable(discountOn(fare, creditLeft))).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal rupeesDown(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.FLOOR);
    }

    String getCode() { return code; }
    PromotionType getType() { return type; }
    BigDecimal getMaxDiscountPerBooking() { return maxDiscountPerBooking; }
    int getMaxUsesPerAccount() { return maxUsesPerAccount; }
    Integer getCreditValidDays() { return creditValidDays; }
}
