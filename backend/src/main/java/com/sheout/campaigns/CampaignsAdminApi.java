package com.sheout.campaigns;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The console's view of campaigns: create, change, pause, and see what each
 * has cost against its budget. Invalid drafts throw
 * {@link CampaignValidationException} with a message for the operator.
 */
public interface CampaignsAdminApi {

    List<PromotionView> listPromotions();

    PromotionView createPromotion(PromotionDraft draft);

    Optional<PromotionView> updatePromotion(UUID id, PromotionDraft draft);

    Optional<PromotionView> setPromotionPaused(UUID id, boolean paused);

    List<IncentiveView> listIncentives();

    IncentiveView createIncentive(IncentiveDraft draft);

    Optional<IncentiveView> updateIncentive(UUID id, IncentiveDraft draft);

    Optional<IncentiveView> setIncentivePaused(UUID id, boolean paused);

    /** Every signup credit granted, and when (and how) it ended - the basis for the retention report. */
    List<SignupCreditOutcome> signupCreditOutcomes();

    record PromotionDraft(String name, String code, PromotionType type, BigDecimal value,
                          BigDecimal maxDiscountPerBooking, Integer maxUsesPerAccount, Integer creditValidDays,
                          Instant validFrom, Instant validUntil, BigDecimal budgetCap) {
    }

    record PromotionView(UUID id, String name, String code, PromotionType type, BigDecimal value,
                         BigDecimal maxDiscountPerBooking, int maxUsesPerAccount, Integer creditValidDays,
                         Instant validFrom, Instant validUntil, BigDecimal budgetCap, BigDecimal spent,
                         BigDecimal remaining, CampaignStatus status, long ridersGranted, long tripsDiscounted,
                         Instant createdAt) {
    }

    record IncentiveDraft(String name, IncentiveType type, BigDecimal value, Integer firstNTrips,
                          Instant validFrom, Instant validUntil, BigDecimal budgetCap) {
    }

    record IncentiveView(UUID id, String name, IncentiveType type, BigDecimal value, Integer firstNTrips,
                         Instant validFrom, Instant validUntil, BigDecimal budgetCap, BigDecimal spent,
                         BigDecimal remaining, CampaignStatus status, long partnersAwarded, long awards,
                         Instant createdAt) {
    }

    /**
     * One rider's signup credit: how much, how much she used, and when it
     * ended - endedAt is when the last of it was spent (EXHAUSTED) or when it
     * lapsed with some left (EXPIRED); null while she can still use it.
     */
    record SignupCreditOutcome(UUID accountId, Instant grantedAt, BigDecimal creditTotal, BigDecimal creditUsed,
                               Instant endedAt, String endedBy) {
    }
}
