package com.sheout.payments.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The platform's cut, and what is left for the partner.
 * <p>
 * Kept as its own thing rather than folded into the fare, because they are
 * two different numbers answering two different questions. The fare is what
 * a rider agreed to pay. The commission is what this business takes for
 * running the service. Baking one into the other would make the platform's
 * revenue impossible to measure from its own records, and would make the
 * driver-facing payout breakdown this project has committed to either
 * dishonest or unbuildable.
 * <p>
 * 18% by default, within the range this industry runs at. It is
 * configuration because it is a decision that will be argued about - by
 * partners, and internally - and the answer to "can you take less" should
 * not be "not until the next deploy".
 */
@Component
public class PlatformCommission {

    private static final Logger log = LoggerFactory.getLogger(PlatformCommission.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal percent;

    PlatformCommission(@Value("${PLATFORM_COMMISSION_PERCENT:18.00}") BigDecimal percent,
                       // The 2025 Motor Vehicle Aggregator Guidelines: a partner
                       // driving her own vehicle receives at least 80% of the
                       // fare. Configurable because the rule, and which partners
                       // it covers, may change; 20% is today's ceiling.
                       @Value("${PLATFORM_COMMISSION_MAX_PERCENT:20.00}") BigDecimal maxPercent) {
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(HUNDRED) > 0) {
            // A commission above 100% would pay a partner a negative amount,
            // and below zero would pay her more than the rider paid. Both are
            // configuration mistakes worth refusing to start over rather than
            // discovering in somebody's earnings.
            throw new IllegalArgumentException(
                    "PLATFORM_COMMISSION_PERCENT must be between 0 and 100, got " + percent);
        }
        if (percent.compareTo(maxPercent) > 0) {
            // Refused at startup, not clamped: a commission quietly cut to the
            // ceiling would be a number nobody chose. Somebody sets it again.
            throw new IllegalArgumentException("PLATFORM_COMMISSION_PERCENT is " + percent
                    + "%, above PLATFORM_COMMISSION_MAX_PERCENT (" + maxPercent + "%). Partners using their own"
                    + " vehicle must receive at least 80% of the fare under the 2025 Motor Vehicle Aggregator"
                    + " Guidelines, so SheOut's commission cannot exceed 20%.");
        }
        this.percent = percent.setScale(2, RoundingMode.HALF_UP);
        log.info("Platform commission: {}% of every captured fare", this.percent);
    }

    public BigDecimal percent() {
        return percent;
    }

    /**
     * What the partner receives from a given fare.
     * <p>
     * Rounded HALF_DOWN, deliberately, and this is the one place in the
     * pricing code that does not round half up. Rounding a payout up would
     * mean the platform's recorded revenue and the sum of its payouts do not
     * add to the fare collected - a rupee appearing from nowhere, on every
     * trip where the split lands on a half paisa. Rounding the partner's
     * share down keeps the books closing, and the amount involved is a
     * fraction of a paisa.
     */
    public BigDecimal payoutFrom(BigDecimal fare) {
        BigDecimal keptFraction = HUNDRED.subtract(percent).divide(HUNDRED, 6, RoundingMode.HALF_UP);
        return fare.multiply(keptFraction).setScale(2, RoundingMode.HALF_DOWN);
    }
}
