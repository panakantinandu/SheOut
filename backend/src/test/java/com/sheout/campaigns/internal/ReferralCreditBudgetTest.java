package com.sheout.campaigns.internal;

import com.sheout.campaigns.CampaignStatus;
import com.sheout.campaigns.PromoApplication;
import com.sheout.campaigns.PromotionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Referral credit is the signup credit's kind of balance, held and spent the
 * same way: granted without touching the budget, and counted against its
 * promotion's budget cap as it is spent on trips - which stops it, like
 * every other promotion, when the cap is reached.
 */
class ReferralCreditBudgetTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final PromotionGrantRepository grants = mock(PromotionGrantRepository.class);
    private final PromotionRedemptionRepository redemptions = mock(PromotionRedemptionRepository.class);
    private final List<PromotionGrantEntity> held = new ArrayList<>();
    private final UUID rider = UUID.randomUUID();
    private PromotionEntity welcome;
    private PromotionService service;

    @BeforeEach
    void setUp() {
        welcome = new PromotionEntity(PromotionType.REFERRAL_WELCOME);
        welcome.apply("Referral welcome", null, new BigDecimal("50"), null, 1, 60, NOW.minusSeconds(60), null,
                new BigDecimal("80"), NOW);
        service = new PromotionService(promotions, grants, redemptions);
        when(promotions.findByType(PromotionType.REFERRAL_WELCOME)).thenReturn(List.of(welcome));
        when(promotions.findById(any())).thenReturn(Optional.of(welcome));
        when(promotions.findLockedById(any())).thenReturn(Optional.of(welcome));
        when(promotions.findByCodeIsNullAndTypeIn(any())).thenReturn(List.of());
        when(grants.save(any())).thenAnswer(inv -> {
            PromotionGrantEntity g = inv.getArgument(0);
            if (!held.contains(g)) held.add(g);
            return g;
        });
        when(grants.findByAccountId(rider)).thenAnswer(inv -> held);
        when(grants.findByPromotionIdAndAccountId(any(), any())).thenAnswer(inv -> held.stream().findFirst());
        when(grants.findLockedById(any())).thenAnswer(inv -> held.stream().findFirst());
    }

    @Test
    void grantingCostsNothingSpendingItCountsAgainstTheBudgetAndTheCapStopsIt() {
        assertThat(service.grantReferralCredit(PromotionType.REFERRAL_WELCOME, rider, Instant.now())).isEqualByComparingTo("50");
        assertThat(welcome.getSpent()).isEqualByComparingTo("0");

        PromoApplication first = service.reserveDiscount(rider, UUID.randomUUID(), new BigDecimal("30"));
        assertThat(first.discount()).isEqualByComparingTo("30");
        assertThat(welcome.getSpent()).isEqualByComparingTo("30");
        assertThat(held.get(0).getCreditRemaining()).isEqualByComparingTo("20");

        // A second friend's reward tops the same balance up.
        service.grantReferralCredit(PromotionType.REFERRAL_WELCOME, rider, Instant.now());
        assertThat(held).hasSize(1);
        assertThat(held.get(0).getCreditRemaining()).isEqualByComparingTo("70");
        assertThat(held.get(0).getCreditTotal()).isEqualByComparingTo("100");

        // Rs 70 left on her balance, but only Rs 50 left in the Rs 80 budget.
        PromoApplication second = service.reserveDiscount(rider, UUID.randomUUID(), new BigDecimal("90"));
        assertThat(second.discount()).isEqualByComparingTo("50");
        assertThat(welcome.getSpent()).isEqualByComparingTo("80");
        assertThat(welcome.statusAt(Instant.now())).isEqualTo(CampaignStatus.BUDGET_REACHED);

        // Stopped by its budget: no more referral credit is given from it.
        assertThat(service.grantReferralCredit(PromotionType.REFERRAL_WELCOME, rider, Instant.now())).isEqualByComparingTo("0");
    }

    @Test
    void referralCreditCannotBeRedeemedAsACode() {
        PromotionEntity coded = new PromotionEntity(PromotionType.REFERRAL_REWARD);
        coded.apply("Odd", "REFER50", new BigDecimal("50"), null, 1, 60, NOW.minusSeconds(60), null, new BigDecimal("500"), NOW);
        when(promotions.findByCodeIgnoreCase("REFER50")).thenReturn(Optional.of(coded));
        assertThat(service.redeemCode(rider, "REFER50")).isEqualTo(PromotionService.RedeemOutcome.UNKNOWN_OR_ENDED);
    }
}
