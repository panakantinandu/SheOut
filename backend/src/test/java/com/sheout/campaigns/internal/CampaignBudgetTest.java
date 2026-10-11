package com.sheout.campaigns.internal;

import com.sheout.campaigns.CampaignStatus;
import com.sheout.campaigns.IncentiveType;
import com.sheout.campaigns.PromotionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignBudgetTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private static PromotionEntity promotion(PromotionType type, String value, String cap) {
        PromotionEntity p = new PromotionEntity(type);
        p.apply("Test", null, new BigDecimal(value), null, 1, 30, NOW.minusSeconds(60), null, new BigDecimal(cap), NOW);
        return p;
    }

    private static BigDecimal rupees(String v) {
        return new BigDecimal(v);
    }

    @Test
    void theLastDiscountIsCutToWhatIsLeftAndThenThePromotionStops() {
        PromotionEntity p = promotion(PromotionType.FLAT_DISCOUNT, "50", "120");
        assertThat(p.affordable(rupees("50"))).isEqualByComparingTo("50");
        p.spend(rupees("50"), NOW);
        p.spend(rupees("50"), NOW);
        assertThat(p.affordable(rupees("50"))).isEqualByComparingTo("20");
        p.spend(rupees("20"), NOW);
        assertThat(p.statusAt(NOW)).isEqualTo(CampaignStatus.BUDGET_REACHED);
        assertThat(p.activeAt(NOW)).isFalse();
        assertThat(p.affordable(rupees("50"))).isEqualByComparingTo("0");
    }

    @Test
    void moneyGivenBackReopensABudgetStopButNotAPause() {
        PromotionEntity p = promotion(PromotionType.FLAT_DISCOUNT, "50", "100");
        p.spend(rupees("100"), NOW);
        assertThat(p.statusAt(NOW)).isEqualTo(CampaignStatus.BUDGET_REACHED);
        p.giveBack(rupees("50"));
        assertThat(p.statusAt(NOW)).isEqualTo(CampaignStatus.ACTIVE);
        p.setPaused(true);
        assertThat(p.statusAt(NOW)).isEqualTo(CampaignStatus.PAUSED);
    }

    @Test
    void raisingTheCapLiftsTheStopAndLoweringItBelowSpendStopsItNow() {
        PromotionEntity p = promotion(PromotionType.FLAT_DISCOUNT, "50", "100");
        p.spend(rupees("100"), NOW);
        p.apply("Test", null, rupees("50"), null, 1, 30, NOW.minusSeconds(60), null, rupees("500"), NOW);
        assertThat(p.statusAt(NOW)).isEqualTo(CampaignStatus.ACTIVE);
        p.apply("Test", null, rupees("50"), null, 1, 30, NOW.minusSeconds(60), null, rupees("80"), NOW);
        assertThat(p.statusAt(NOW)).isEqualTo(CampaignStatus.BUDGET_REACHED);
    }

    @Test
    void datesDecideScheduledAndEnded() {
        PromotionEntity p = new PromotionEntity(PromotionType.FLAT_DISCOUNT);
        p.apply("Test", null, rupees("10"), null, 1, null, NOW.plusSeconds(3600), NOW.plusSeconds(7200), rupees("100"), NOW);
        assertThat(p.statusAt(NOW)).isEqualTo(CampaignStatus.SCHEDULED);
        assertThat(p.statusAt(NOW.plusSeconds(7200))).isEqualTo(CampaignStatus.ENDED);
    }

    @Test
    void discountsNeverExceedTheFare() {
        assertThat(promotion(PromotionType.SIGNUP_CREDIT, "100", "1000").discountOn(rupees("40"), rupees("100"))).isEqualByComparingTo("40");
        assertThat(promotion(PromotionType.SIGNUP_CREDIT, "100", "1000").discountOn(rupees("80"), rupees("60"))).isEqualByComparingTo("60");
        assertThat(promotion(PromotionType.FLAT_DISCOUNT, "50", "1000").discountOn(rupees("30"), null)).isEqualByComparingTo("30");
        PromotionEntity pct = new PromotionEntity(PromotionType.PERCENTAGE_DISCOUNT);
        pct.apply("Test", null, rupees("20"), rupees("15"), 1, null, NOW.minusSeconds(60), null, rupees("1000"), NOW);
        assertThat(pct.discountOn(rupees("50"), null)).isEqualByComparingTo("10");   // 20% of 50
        assertThat(pct.discountOn(rupees("200"), null)).isEqualByComparingTo("15");  // 40, bounded at 15
    }

    private static PromotionEntity percentage(String percent, String perTripCap, String budget) {
        PromotionEntity p = new PromotionEntity(PromotionType.PERCENTAGE_DISCOUNT);
        p.apply("Test", null, rupees(percent), perTripCap == null ? null : rupees(perTripCap), 1, null, NOW.minusSeconds(60), null, rupees(budget), NOW);
        return p;
    }

    /** What she pays: the whole fare less the discount. */
    private static BigDecimal payable(PromotionEntity p, String fare, String creditLeft) {
        return rupees(fare).subtract(p.wholeDiscountOn(rupees(fare), creditLeft == null ? null : rupees(creditLeft)));
    }

    @Test
    void aPercentageDiscountIsWholeRupeesRoundedHalfUp() {
        assertThat(percentage("10", null, "1000").wholeDiscountOn(rupees("107"), null)).isEqualByComparingTo("11"); // 10.70
        assertThat(percentage("10", null, "1000").wholeDiscountOn(rupees("104"), null)).isEqualByComparingTo("10"); // 10.40
        assertThat(percentage("10", null, "1000").wholeDiscountOn(rupees("105"), null)).isEqualByComparingTo("11"); // 10.50
        assertThat(payable(percentage("10", null, "1000"), "107", null)).isEqualByComparingTo("96");
    }

    @Test
    void aPerTripCapWithPaiseIsNeverExceeded() {
        // 50% of 107 = 53.50 -> 54, held to a cap of 25.50 -> 25 (down, never above the cap).
        assertThat(percentage("50", "25.50", "1000").wholeDiscountOn(rupees("107"), null)).isEqualByComparingTo("25");
    }

    @Test
    void flatAmountsAndCreditsWithPaiseRoundDownNeverOverGiving() {
        assertThat(promotion(PromotionType.FLAT_DISCOUNT, "10.50", "1000").wholeDiscountOn(rupees("107"), null)).isEqualByComparingTo("10");
        // A balance left with paise by older fares: 22.56 -> 22; the 0.56 stays in the balance.
        assertThat(promotion(PromotionType.SIGNUP_CREDIT, "100", "1000").wholeDiscountOn(rupees("77"), rupees("22.56"))).isEqualByComparingTo("22");
    }

    @Test
    void aBudgetRemainderWithPaiseRoundsDownSoTheBudgetIsNeverOverspent() {
        PromotionEntity p = percentage("10", null, "1000");
        p.spend(rupees("995.40"), NOW); // 4.60 left
        assertThat(p.wholeDiscountOn(rupees("107"), null)).isEqualByComparingTo("4");
    }

    @Test
    void whatShePaysIsAlwaysWholeAndNeverBelowZero() {
        assertThat(payable(percentage("100", null, "1000"), "77", null)).isEqualByComparingTo("0");
        assertThat(payable(percentage("150", null, "1000"), "77", null)).isEqualByComparingTo("0");
        assertThat(payable(promotion(PromotionType.FLAT_DISCOUNT, "500", "1000"), "77", null)).isEqualByComparingTo("0");
        assertThat(payable(promotion(PromotionType.SIGNUP_CREDIT, "100", "1000"), "77", "100")).isEqualByComparingTo("0");
        for (String fare : new String[] {"40", "77", "107", "253", "1250"}) {
            for (String pct : new String[] {"5", "12.5", "15", "33", "99"}) {
                BigDecimal pays = payable(percentage(pct, null, "100000"), fare, null);
                assertThat(pays.stripTrailingZeros().scale()).as("%s%% of %s", pct, fare).isLessThanOrEqualTo(0);
                assertThat(pays.signum()).isGreaterThanOrEqualTo(0);
            }
        }
    }

    @Test
    void aSignupCreditIsHeldReleasedAndMarkedSpentWhenItRunsOut() {
        PromotionGrantEntity g = PromotionGrantEntity.credit(null, null, rupees("100"), NOW, NOW.plusSeconds(86400));
        g.hold(rupees("40"));
        assertThat(g.getCreditRemaining()).isEqualByComparingTo("60");
        g.release(rupees("40"));
        assertThat(g.getCreditRemaining()).isEqualByComparingTo("100");
        g.hold(rupees("100"));
        g.settle(NOW, true);
        assertThat(g.getExhaustedAt()).isEqualTo(NOW);
        assertThat(g.usableAt(NOW)).isFalse();
        assertThat(PromotionGrantEntity.credit(null, null, rupees("100"), NOW, NOW.plusSeconds(10)).usableAt(NOW.plusSeconds(10))).isFalse();
    }

    @Test
    void incentiveRules() {
        DriverIncentiveEntity bonus = new DriverIncentiveEntity(IncentiveType.PER_TRIP_BONUS);
        bonus.apply("First trips", rupees("30"), 5, NOW.minusSeconds(60), null, rupees("1000"), NOW);
        assertThat(IncentiveRules.amountFor(bonus, new IncentiveRules.Trip(rupees("60"), 1))).isEqualByComparingTo("30");
        assertThat(IncentiveRules.amountFor(bonus, new IncentiveRules.Trip(rupees("60"), 5))).isEqualByComparingTo("30");
        assertThat(IncentiveRules.amountFor(bonus, new IncentiveRules.Trip(rupees("60"), 6))).isEqualByComparingTo("0");

        DriverIncentiveEntity floor = new DriverIncentiveEntity(IncentiveType.MINIMUM_EARNINGS_GUARANTEE);
        floor.apply("At least 50", rupees("50"), null, NOW.minusSeconds(60), null, rupees("1000"), NOW);
        assertThat(IncentiveRules.amountFor(floor, new IncentiveRules.Trip(rupees("32.40"), 99))).isEqualByComparingTo("17.60");
        assertThat(IncentiveRules.amountFor(floor, new IncentiveRules.Trip(rupees("70"), 1))).isEqualByComparingTo("0");
    }
}
