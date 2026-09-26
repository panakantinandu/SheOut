package com.sheout.campaigns.internal;

import com.sheout.campaigns.CampaignValidationException;
import com.sheout.campaigns.CampaignsAdminApi;
import com.sheout.campaigns.PromotionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Campaigns as the console sees them. Changing a promotion's value affects
 * what is granted and applied from now on; credit already granted keeps its
 * amount, and money already paid out stays paid.
 */
@Service
public class CampaignsAdminService implements CampaignsAdminApi {

    private final PromotionRepository promotions;
    private final PromotionGrantRepository grants;
    private final PromotionRedemptionRepository redemptions;
    private final DriverIncentiveRepository incentives;
    private final IncentiveAwardRepository awards;

    public CampaignsAdminService(PromotionRepository promotions, PromotionGrantRepository grants,
                                 PromotionRedemptionRepository redemptions, DriverIncentiveRepository incentives,
                                 IncentiveAwardRepository awards) {
        this.promotions = promotions;
        this.grants = grants;
        this.redemptions = redemptions;
        this.incentives = incentives;
        this.awards = awards;
    }

    // ------------------------------------------------------------ promotions

    @Override
    @Transactional(readOnly = true)
    public List<PromotionView> listPromotions() {
        Instant now = Instant.now();
        return promotions.findAllByOrderByCreatedAtDesc().stream().map(p -> view(p, now)).toList();
    }

    @Override
    @Transactional
    public PromotionView createPromotion(PromotionDraft draft) {
        validate(draft, null);
        Instant now = Instant.now();
        PromotionEntity p = new PromotionEntity(draft.type());
        applyDraft(p, draft, now);
        return view(promotions.save(p), now);
    }

    @Override
    @Transactional
    public Optional<PromotionView> updatePromotion(UUID id, PromotionDraft draft) {
        Instant now = Instant.now();
        return promotions.findLockedById(id).map(p -> {
            if (draft.type() != null && draft.type() != p.getType()) {
                throw new CampaignValidationException("A promotion's type cannot change. Create a new promotion instead.");
            }
            validate(draft, id);
            applyDraft(p, draft, now);
            return view(promotions.save(p), now);
        });
    }

    @Override
    @Transactional
    public Optional<PromotionView> setPromotionPaused(UUID id, boolean paused) {
        Instant now = Instant.now();
        return promotions.findLockedById(id).map(p -> {
            p.setPaused(paused);
            return view(promotions.save(p), now);
        });
    }

    private void applyDraft(PromotionEntity p, PromotionDraft d, Instant now) {
        p.apply(d.name().trim(), PromotionService.normaliseCode(d.code()), d.value(), d.maxDiscountPerBooking(),
                d.maxUsesPerAccount() == null ? 1 : d.maxUsesPerAccount(), d.creditValidDays(),
                d.validFrom() == null ? now : d.validFrom(), d.validUntil(), d.budgetCap(), now);
    }

    private void validate(PromotionDraft d, UUID existingId) {
        if (d.type() == null) throw new CampaignValidationException("Choose a promotion type.");
        requireName(d.name());
        requirePositive(d.value(), "Value");
        requirePositive(d.budgetCap(), "Budget cap");
        if (d.type() == PromotionType.PERCENTAGE_DISCOUNT && d.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CampaignValidationException("A percentage discount cannot be more than 100%.");
        }
        if (d.maxUsesPerAccount() != null && d.maxUsesPerAccount() < 1) {
            throw new CampaignValidationException("Uses per rider must be at least 1.");
        }
        if (d.creditValidDays() != null && d.creditValidDays() < 1) {
            throw new CampaignValidationException("Credit validity must be at least 1 day.");
        }
        requireDates(d.validFrom(), d.validUntil());
        String code = PromotionService.normaliseCode(d.code());
        if (code != null) {
            if (!code.matches("[A-Z0-9]{4,20}")) {
                throw new CampaignValidationException("A code is 4-20 letters and digits, e.g. WELCOME50.");
            }
            promotions.findByCodeIgnoreCase(code).filter(other -> !other.getId().equals(existingId)).ifPresent(other -> {
                throw new CampaignValidationException("Another promotion already uses the code " + code + ".");
            });
        }
    }

    private PromotionView view(PromotionEntity p, Instant now) {
        return new PromotionView(p.getId(), p.getName(), p.getCode(), p.getType(), p.getValue(),
                p.getMaxDiscountPerBooking(), p.getMaxUsesPerAccount(), p.getCreditValidDays(),
                p.getValidFrom(), p.getValidUntil(), p.getBudgetCap(), p.getSpent(), p.remaining(),
                p.statusAt(now), grants.countByPromotionId(p.getId()),
                redemptions.countByPromotionIdAndStatus(p.getId(), PromotionRedemptionEntity.Status.CONSUMED),
                p.getCreatedAt());
    }

    // ------------------------------------------------------------ incentives

    @Override
    @Transactional(readOnly = true)
    public List<IncentiveView> listIncentives() {
        Instant now = Instant.now();
        return incentives.findAllByOrderByCreatedAtDesc().stream().map(i -> view(i, now)).toList();
    }

    @Override
    @Transactional
    public IncentiveView createIncentive(IncentiveDraft draft) {
        validate(draft);
        Instant now = Instant.now();
        DriverIncentiveEntity i = new DriverIncentiveEntity(draft.type());
        i.apply(draft.name().trim(), draft.value(), draft.firstNTrips(),
                draft.validFrom() == null ? now : draft.validFrom(), draft.validUntil(), draft.budgetCap(), now);
        return view(incentives.save(i), now);
    }

    @Override
    @Transactional
    public Optional<IncentiveView> updateIncentive(UUID id, IncentiveDraft draft) {
        Instant now = Instant.now();
        return incentives.findLockedById(id).map(i -> {
            if (draft.type() != null && draft.type() != i.getType()) {
                throw new CampaignValidationException("An incentive's type cannot change. Create a new incentive instead.");
            }
            validate(draft);
            i.apply(draft.name().trim(), draft.value(), draft.firstNTrips(),
                    draft.validFrom() == null ? i.getValidFrom() : draft.validFrom(), draft.validUntil(), draft.budgetCap(), now);
            return view(incentives.save(i), now);
        });
    }

    @Override
    @Transactional
    public Optional<IncentiveView> setIncentivePaused(UUID id, boolean paused) {
        Instant now = Instant.now();
        return incentives.findLockedById(id).map(i -> {
            i.setPaused(paused);
            return view(incentives.save(i), now);
        });
    }

    private void validate(IncentiveDraft d) {
        if (d.type() == null) throw new CampaignValidationException("Choose an incentive type.");
        requireName(d.name());
        requirePositive(d.value(), "Value");
        requirePositive(d.budgetCap(), "Budget cap");
        if (d.firstNTrips() != null && d.firstNTrips() < 1) {
            throw new CampaignValidationException("\"First N trips\" must be at least 1, or left empty for every trip.");
        }
        requireDates(d.validFrom(), d.validUntil());
    }

    private IncentiveView view(DriverIncentiveEntity i, Instant now) {
        return new IncentiveView(i.getId(), i.getName(), i.getType(), i.getValue(), i.getFirstNTrips(),
                i.getValidFrom(), i.getValidUntil(), i.getBudgetCap(), i.getSpent(), i.remaining(), i.statusAt(now),
                awards.countDistinctDriversByIncentiveId(i.getId()), awards.countByIncentiveId(i.getId()), i.getCreatedAt());
    }

    // ------------------------------------------------------------ the retention report

    @Override
    @Transactional(readOnly = true)
    public List<SignupCreditOutcome> signupCreditOutcomes() {
        Instant now = Instant.now();
        List<UUID> signupPromotions = promotions.findByType(PromotionType.SIGNUP_CREDIT).stream().map(PromotionEntity::getId).toList();
        if (signupPromotions.isEmpty()) {
            return List.of();
        }
        return grants.findByPromotionIdIn(signupPromotions).stream().map(g -> {
            BigDecimal used = g.getCreditTotal().subtract(g.getCreditRemaining());
            Instant endedAt = null;
            String endedBy = null;
            if (g.getExhaustedAt() != null) {
                endedAt = g.getExhaustedAt();
                endedBy = "EXHAUSTED";
            } else if (g.getExpiresAt() != null && !now.isBefore(g.getExpiresAt())) {
                endedAt = g.getExpiresAt();
                endedBy = "EXPIRED";
            }
            return new SignupCreditOutcome(g.getAccountId(), g.getGrantedAt(), g.getCreditTotal(), used, endedAt, endedBy);
        }).toList();
    }

    // ------------------------------------------------------------ shared

    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 120) {
            throw new CampaignValidationException("Give it a name (up to 120 characters).");
        }
    }

    private static void requirePositive(BigDecimal amount, String what) {
        if (amount == null || amount.signum() <= 0) {
            throw new CampaignValidationException(what + " must be more than zero.");
        }
    }

    private static void requireDates(Instant from, Instant until) {
        if (from != null && until != null && !until.isAfter(from)) {
            throw new CampaignValidationException("The end date must be after the start date.");
        }
    }
}
