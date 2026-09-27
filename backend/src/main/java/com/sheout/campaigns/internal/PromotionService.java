package com.sheout.campaigns.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingNoDriversAvailable;
import com.sheout.campaigns.CampaignsApi;
import com.sheout.campaigns.PromoApplication;
import com.sheout.campaigns.PromotionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Rider promotions: who holds what, what a fare's discount is, and the
 * budget each promotion has spent.
 * <p>
 * THE LIFE OF A DISCOUNT. A quote only previews it. Booking reserves it -
 * the credit or use is held and the amount counted against the budget at
 * that moment, so two bookings cannot both spend the last of a budget. The
 * trip completing consumes it for good; the trip being cancelled, or finding
 * no partner, releases it and gives it all back.
 * <p>
 * ONE PROMOTION PER TRIP: the one that takes the most off, so a rider never
 * has to choose and never loses to the order things were set up in.
 */
@Service
public class PromotionService implements CampaignsApi {

    private static final Logger log = LoggerFactory.getLogger(PromotionService.class);
    /** The promotions that are a discount on each trip rather than a balance - the ones that can apply to everyone. */
    private static final EnumSet<PromotionType> PER_TRIP_TYPES =
            EnumSet.of(PromotionType.PERCENTAGE_DISCOUNT, PromotionType.FLAT_DISCOUNT);
    private static final EnumSet<PromotionRedemptionEntity.Status> HELD_OR_USED =
            EnumSet.of(PromotionRedemptionEntity.Status.RESERVED, PromotionRedemptionEntity.Status.CONSUMED);

    private final PromotionRepository promotions;
    private final PromotionGrantRepository grants;
    private final PromotionRedemptionRepository redemptions;

    public PromotionService(PromotionRepository promotions, PromotionGrantRepository grants,
                            PromotionRedemptionRepository redemptions) {
        this.promotions = promotions;
        this.grants = grants;
        this.redemptions = redemptions;
    }

    // ------------------------------------------------------------ signup

    /**
     * Every new rider is given each signup credit that is running when she
     * signs up. After the signup commits and in a transaction of its own: a
     * promotion must never be able to break signing up.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAccountRegistered(AccountRegistered event) {
        if (event.role() != AccountRole.CUSTOMER) {
            return;
        }
        Instant now = Instant.now();
        for (PromotionEntity promotion : promotions.findByType(PromotionType.SIGNUP_CREDIT)) {
            if (!promotion.activeAt(now) || grants.findByPromotionIdAndAccountId(promotion.getId(), event.accountId()).isPresent()) {
                continue;
            }
            grants.save(PromotionGrantEntity.credit(promotion.getId(), event.accountId(), promotion.getValue(), now,
                    expiryFor(promotion, now)));
            log.info("Signup credit {} granted to new rider {}", promotion.getValue(), event.accountId());
        }
    }

    private static Instant expiryFor(PromotionEntity promotion, Instant now) {
        Instant byDays = promotion.getCreditValidDays() == null ? null : now.plus(Duration.ofDays(promotion.getCreditValidDays()));
        Instant byCampaign = promotion.getValidUntil();
        if (byDays == null) return byCampaign;
        if (byCampaign == null) return byDays;
        return byDays.isBefore(byCampaign) ? byDays : byCampaign;
    }

    // ------------------------------------------------------------ referrals

    /**
     * A referral credit for this rider, from the running promotion of this
     * type - the same kind of balance as the signup credit, held and spent
     * the same way, and counted against that promotion's budget as she
     * spends it. A rider rewarded for a second friend has her balance topped
     * up rather than given a second one.
     * <p>
     * Nothing when no such promotion is running - paused, ended, or stopped
     * by its budget: a budget that has run out stops referral credit exactly
     * as it stops every other promotion. Returns what was granted.
     */
    @Transactional
    public BigDecimal grantReferralCredit(PromotionType type, UUID accountId, Instant now) {
        if (type != PromotionType.REFERRAL_REWARD && type != PromotionType.REFERRAL_WELCOME) {
            throw new IllegalArgumentException("Not a referral promotion: " + type);
        }
        Optional<PromotionEntity> running = runningReferralPromotion(type, now);
        if (running.isEmpty()) {
            return BigDecimal.ZERO;
        }
        PromotionEntity promotion = running.get();
        BigDecimal amount = promotion.getValue();
        Instant expiry = expiryFor(promotion, now);
        grants.findByPromotionIdAndAccountId(promotion.getId(), accountId)
                .flatMap(g -> grants.findLockedById(g.getId()))
                .ifPresentOrElse(g -> {
                    g.topUp(amount, expiry);
                    grants.save(g);
                }, () -> grants.save(PromotionGrantEntity.credit(promotion.getId(), accountId, amount, now, expiry)));
        log.info("{} credit {} granted to rider {} from '{}'", type, amount, accountId, promotion.getName());
        return amount;
    }

    /** What the running promotion of this type gives, for the Refer screen; empty when none is running. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> referralCreditAmount(PromotionType type) {
        return runningReferralPromotion(type, Instant.now()).map(PromotionEntity::getValue);
    }

    /** The newest running one, if the console holds more than one of a type. */
    private Optional<PromotionEntity> runningReferralPromotion(PromotionType type, Instant now) {
        return promotions.findByType(type).stream()
                .filter(p -> p.activeAt(now))
                .max(java.util.Comparator.comparing(PromotionEntity::getCreatedAt, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
    }

    // ------------------------------------------------------------ codes

    public enum RedeemOutcome { REDEEMED, ALREADY_REDEEMED, UNKNOWN_OR_ENDED }

    /** A rider enters a promotion's code; she then holds its uses. Unknown, paused, ended and spent codes all read the same. */
    @Transactional
    public RedeemOutcome redeemCode(UUID customerId, String code) {
        Instant now = Instant.now();
        Optional<PromotionEntity> found = promotions.findByCodeIgnoreCase(code.trim())
                .filter(p -> !p.getType().isCredit())
                .filter(p -> p.activeAt(now));
        if (found.isEmpty()) {
            return RedeemOutcome.UNKNOWN_OR_ENDED;
        }
        PromotionEntity promotion = found.get();
        if (grants.findByPromotionIdAndAccountId(promotion.getId(), customerId).isPresent()) {
            return RedeemOutcome.ALREADY_REDEEMED;
        }
        grants.save(PromotionGrantEntity.uses(promotion.getId(), customerId, promotion.getMaxUsesPerAccount(), now,
                promotion.getValidUntil()));
        return RedeemOutcome.REDEEMED;
    }

    // ------------------------------------------------------------ discounts

    /** One way a promotion could apply to this rider now, with what it would take off. */
    private record Candidate(PromotionEntity promotion, PromotionGrantEntity grant, BigDecimal discount) {
    }

    /** Every promotion this rider could use on this fare right now, best first. */
    private List<Candidate> candidates(UUID customerId, BigDecimal fare, Instant now) {
        List<Candidate> found = new ArrayList<>();
        for (PromotionGrantEntity grant : grants.findByAccountId(customerId)) {
            if (!grant.usableAt(now)) continue;
            promotions.findById(grant.getPromotionId())
                    .filter(p -> p.activeAt(now))
                    .ifPresent(p -> found.add(new Candidate(p, grant,
                            p.affordable(p.discountOn(fare, grant.getCreditRemaining())))));
        }
        // Promotions with no code that apply to every rider, up to their per-rider limit.
        for (PromotionEntity p : promotions.findByCodeIsNullAndTypeIn(PER_TRIP_TYPES)) {
            if (!p.activeAt(now)) continue;
            long used = redemptions.countByPromotionIdAndAccountIdAndStatusIn(p.getId(), customerId, HELD_OR_USED);
            if (used >= p.getMaxUsesPerAccount()) continue;
            found.add(new Candidate(p, null, p.affordable(p.discountOn(fare, null))));
        }
        found.removeIf(c -> c.discount().signum() <= 0);
        found.sort((a, b) -> b.discount().compareTo(a.discount()));
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public PromoApplication previewDiscount(UUID customerId, BigDecimal fare) {
        return candidates(customerId, fare, Instant.now()).stream().findFirst()
                .map(c -> new PromoApplication(c.discount(), c.promotion().getId(), c.promotion().getName()))
                .orElse(PromoApplication.none());
    }

    /**
     * Holds the best discount for this booking. The promotion and grant are
     * locked and the discount recomputed under the lock, so a budget or a
     * credit that another booking has just used up is seen as used up.
     */
    @Override
    @Transactional
    public PromoApplication reserveDiscount(UUID customerId, UUID bookingId, BigDecimal fare) {
        Instant now = Instant.now();
        for (Candidate candidate : candidates(customerId, fare, now)) {
            PromotionEntity promotion = promotions.findLockedById(candidate.promotion().getId()).orElse(null);
            if (promotion == null || !promotion.activeAt(now)) continue;
            PromotionGrantEntity grant = candidate.grant() == null ? null
                    : grants.findLockedById(candidate.grant().getId()).filter(g -> g.usableAt(now)).orElse(null);
            if (candidate.grant() != null && grant == null) continue;
            BigDecimal discount = promotion.affordable(
                    promotion.discountOn(fare, grant == null ? null : grant.getCreditRemaining()));
            if (discount.signum() <= 0) continue;

            promotion.spend(discount, now);
            if (grant != null) {
                grant.hold(discount);
                grants.save(grant);
            }
            promotions.save(promotion);
            redemptions.save(new PromotionRedemptionEntity(promotion.getId(), grant == null ? null : grant.getId(),
                    customerId, bookingId, fare, discount));
            if (promotion.statusAt(now) == com.sheout.campaigns.CampaignStatus.BUDGET_REACHED) {
                log.warn("Promotion '{}' reached its budget cap of {} and has stopped applying", promotion.getName(),
                        promotion.getBudgetCap());
            }
            return new PromoApplication(discount, promotion.getId(), promotion.getName());
        }
        return PromoApplication.none();
    }

    /** The trip happened: the discount is spent for good. */
    @EventListener
    @Transactional
    public void onBookingCompleted(BookingCompleted event) {
        redemptions.findLockedByBookingId(event.bookingId())
                .filter(r -> r.getStatus() == PromotionRedemptionEntity.Status.RESERVED)
                .ifPresent(r -> {
                    r.setStatus(PromotionRedemptionEntity.Status.CONSUMED);
                    redemptions.save(r);
                    if (r.getGrantId() != null) {
                        grants.findLockedById(r.getGrantId()).ifPresent(g -> {
                            g.settle(Instant.now(), redemptions.countByGrantIdAndStatus(g.getId(), PromotionRedemptionEntity.Status.RESERVED) == 0);
                            grants.save(g);
                        });
                    }
                });
    }

    @EventListener
    @Transactional
    public void onBookingCancelled(BookingCancelled event) {
        release(event.bookingId());
    }

    @EventListener
    @Transactional
    public void onNoDriversAvailable(BookingNoDriversAvailable event) {
        release(event.bookingId());
    }

    /** The trip never happened: her credit or use comes back, and so does the budget. */
    private void release(UUID bookingId) {
        redemptions.findLockedByBookingId(bookingId)
                .filter(r -> r.getStatus() == PromotionRedemptionEntity.Status.RESERVED)
                .ifPresent(r -> {
                    r.setStatus(PromotionRedemptionEntity.Status.RELEASED);
                    redemptions.save(r);
                    promotions.findLockedById(r.getPromotionId()).ifPresent(p -> {
                        p.giveBack(r.getDiscount());
                        promotions.save(p);
                    });
                    if (r.getGrantId() != null) {
                        grants.findLockedById(r.getGrantId()).ifPresent(g -> {
                            g.release(r.getDiscount());
                            grants.save(g);
                        });
                    }
                });
    }

    // ------------------------------------------------------------ her own view

    /** A credit or code she holds, for her Wallet screen. */
    public record HeldPromotion(String name, PromotionType type, BigDecimal creditLeft, BigDecimal creditTotal,
                                Integer usesLeft, Instant expiresAt) {
    }

    @Transactional(readOnly = true)
    public List<HeldPromotion> heldBy(UUID customerId) {
        Instant now = Instant.now();
        List<HeldPromotion> held = new ArrayList<>();
        for (PromotionGrantEntity g : grants.findByAccountId(customerId)) {
            if (!g.usableAt(now)) continue;
            promotions.findById(g.getPromotionId()).filter(p -> p.activeAt(now)).ifPresent(p -> held.add(new HeldPromotion(
                    p.getName(), p.getType(), g.getCreditRemaining(), g.getCreditTotal(), g.getUsesRemaining(), g.getExpiresAt())));
        }
        return held;
    }

    static String normaliseCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
