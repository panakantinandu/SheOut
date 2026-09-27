package com.sheout.campaigns.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingParticipants;
import com.sheout.booking.BookingStatus;
import com.sheout.campaigns.IncentiveType;
import com.sheout.campaigns.PromotionType;
import com.sheout.payments.PaymentCaptured;
import com.sheout.payments.PaymentMethod;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Refer a friend.
 * <p>
 * THE CODE. Every rider and partner has one, made at signup: eight letters
 * and digits with nothing that reads two ways (no 0/O, 1/I). A code brings in
 * an account of its own kind - a rider's code brings riders, a partner's
 * brings partners - because each side is rewarded in its own currency.
 * <p>
 * THE FRIEND. She enters the code while setting up her new account, which
 * records the referral as PENDING. Only a new account can: one that has
 * existed for a few days at most and has never finished a trip, so her first
 * paid trip really is her first.
 * <p>
 * THE REWARD, ON HER FIRST PAID TRIP - not at signup, which costs nothing to
 * fake. For a rider that is a trip she paid for herself, so a first trip paid
 * wholly by her signup credit does not count; for a partner, a trip she
 * drove and was paid for. Then both are rewarded:
 * <ul>
 *   <li>riders with credit from the Referral reward / Referral welcome
 *       promotions - the same balance the signup credit is, spent across
 *       trips and counted against that promotion's budget as it is spent;</li>
 *   <li>partners with money in their wallet from the Partner referral
 *       incentives - counted against that incentive's budget when paid.</li>
 * </ul>
 * Amounts, validity, budgets and pausing all live in the console with every
 * other campaign; when one is paused, ended or out of budget, that side gets
 * nothing and the referral records why. There is no second credit system
 * here.
 * <p>
 * LIMITS. A referrer is rewarded for at most sheout.campaigns.referral.max-
 * rewarded-per-referrer friends; her friends beyond that are still welcomed.
 * An account cannot use its own code, and two accounts are the same person
 * when they share a phone number or Google email (AuthApi.samePerson) or
 * have used the referral screens from the same app install - checked when
 * the code is entered and again before anything is paid.
 * <p>
 * The install check catches the obvious case - one phone, two accounts - and
 * nothing cleverer: a cleared browser or a second phone gets past it. Every
 * referral is kept with its outcome so the pattern can be looked for.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    /** No 0/O or 1/I: codes are read aloud and typed from a message. */
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final Pattern INSTALL_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final ReferralCodeRepository codes;
    private final ReferralRepository referrals;
    private final ReferralDeviceRepository devices;
    private final PromotionService promotions;
    private final IncentiveService incentives;
    private final AuthApi authApi;
    private final ObjectProvider<BookingApi> bookingApi;
    private final int maxRewardedPerReferrer;
    private final Duration applyWindow;
    private final String riderLinkBase;
    private final String partnerLinkBase;
    private final SecureRandom random = new SecureRandom();

    public ReferralService(ReferralCodeRepository codes, ReferralRepository referrals, ReferralDeviceRepository devices,
                           PromotionService promotions, IncentiveService incentives, AuthApi authApi,
                           ObjectProvider<BookingApi> bookingApi,
                           @Value("${sheout.campaigns.referral.max-rewarded-per-referrer:10}") int maxRewardedPerReferrer,
                           // How long after signing up a new account may still enter a code.
                           @Value("${sheout.campaigns.referral.apply-within-days:7}") int applyWithinDays,
                           @Value("${sheout.campaigns.referral.rider-link:https://app.sheoutride.com/}") String riderLinkBase,
                           @Value("${sheout.campaigns.referral.partner-link:https://partner.sheoutride.com/}") String partnerLinkBase) {
        this.codes = codes;
        this.referrals = referrals;
        this.devices = devices;
        this.promotions = promotions;
        this.incentives = incentives;
        this.authApi = authApi;
        this.bookingApi = bookingApi;
        this.maxRewardedPerReferrer = maxRewardedPerReferrer;
        this.applyWindow = Duration.ofDays(applyWithinDays);
        this.riderLinkBase = riderLinkBase;
        this.partnerLinkBase = partnerLinkBase;
    }

    // ------------------------------------------------------------ her code

    /** Every new rider and partner gets a code. After the signup commits, in its own transaction: this must never break signing up. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAccountRegistered(AccountRegistered event) {
        if (event.role() == AccountRole.CUSTOMER || event.role() == AccountRole.DRIVER) {
            ensureCode(event.accountId(), event.role());
        }
    }

    private ReferralCodeEntity ensureCode(UUID accountId, AccountRole role) {
        return codes.findByAccountId(accountId).orElseGet(() -> {
            String code;
            do {
                StringBuilder b = new StringBuilder(CODE_LENGTH);
                for (int i = 0; i < CODE_LENGTH; i++) b.append(ALPHABET[random.nextInt(ALPHABET.length)]);
                code = b.toString();
            } while (codes.existsByCode(code));
            return codes.save(new ReferralCodeEntity(accountId, role, code));
        });
    }

    /** What she did it from, kept so that a friend applying from the same install is recognised. */
    private void noteInstall(UUID accountId, String installId, Instant now) {
        if (installId == null || !INSTALL_ID.matcher(installId).matches()) {
            return;
        }
        devices.findByAccountIdAndInstallId(accountId, installId).ifPresentOrElse(
                d -> d.seen(now),
                () -> devices.save(new ReferralDeviceEntity(accountId, installId, now)));
    }

    // ------------------------------------------------------------ her screen

    /** How her own sign-up with a code is going, when she had one. */
    public record JoinedWith(ReferralEntity.Status status, BigDecimal reward) {
    }

    /**
     * Everything the Refer a Friend screen shows. The two amounts are what the
     * running campaigns give now (null when that side is not running), so the
     * screen never promises a reward that would not be paid.
     */
    public record ReferralSummary(
            String code,
            String shareUrl,
            AccountRole role,
            long successful,
            long rewarded,
            int maxRewarded,
            BigDecimal totalEarned,
            long pending,
            BigDecimal referrerAmount,
            BigDecimal refereeAmount,
            /** True for partners, whose reward is money in the wallet; riders' is ride credit. */
            boolean cashReward,
            /** Whether this account may still enter somebody's code - the signup screen's question. */
            boolean canApplyCode,
            JoinedWith joinedWith
    ) {
    }

    @Transactional
    public ReferralSummary summary(UUID accountId, AccountRole role, String installId) {
        Instant now = Instant.now();
        ReferralCodeEntity code = ensureCode(accountId, role);
        noteInstall(accountId, installId, now);
        List<ReferralEntity> mine = referrals.findByReferrerAccountId(accountId);
        long successful = mine.stream().filter(r -> r.getStatus() == ReferralEntity.Status.COMPLETED).count();
        long rewarded = mine.stream().filter(r -> r.getStatus() == ReferralEntity.Status.COMPLETED
                && r.getReferrerReward() != null && r.getReferrerReward().signum() > 0).count();
        long pending = mine.stream().filter(r -> r.getStatus() == ReferralEntity.Status.PENDING).count();
        BigDecimal earned = mine.stream().filter(r -> r.getStatus() == ReferralEntity.Status.COMPLETED)
                .map(r -> r.getReferrerReward() == null ? BigDecimal.ZERO : r.getReferrerReward())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean partner = role == AccountRole.DRIVER;
        BigDecimal referrerAmount = partner
                ? incentives.referralAmount(IncentiveType.REFERRAL_REWARD).orElse(null)
                : promotions.referralCreditAmount(PromotionType.REFERRAL_REWARD).orElse(null);
        BigDecimal refereeAmount = partner
                ? incentives.referralAmount(IncentiveType.REFERRAL_WELCOME).orElse(null)
                : promotions.referralCreditAmount(PromotionType.REFERRAL_WELCOME).orElse(null);
        JoinedWith joined = referrals.findByRefereeAccountId(accountId)
                .map(r -> new JoinedWith(r.getStatus(), r.getRefereeReward()))
                .orElse(null);
        String link = (partner ? partnerLinkBase : riderLinkBase) + "?ref=" + code.getCode();
        return new ReferralSummary(code.getCode(), link, role, successful, rewarded, maxRewardedPerReferrer, earned,
                pending, referrerAmount, refereeAmount, partner,
                joined == null && newEnough(accountId, now), joined);
    }

    // ------------------------------------------------------------ entering a code

    public enum ApplyOutcome { APPLIED, INVALID_CODE, OWN_CODE, ALREADY_APPLIED, NOT_ELIGIBLE }

    /**
     * A new account enters a friend's code. Unknown codes, codes from the
     * other app and codes of blocked accounts all read the same, so the
     * endpoint says nothing about which codes exist.
     */
    @Transactional
    public ApplyOutcome apply(UUID refereeId, AccountRole role, String rawCode, String installId) {
        Instant now = Instant.now();
        noteInstall(refereeId, installId, now);
        if (referrals.findByRefereeAccountId(refereeId).isPresent()) {
            return ApplyOutcome.ALREADY_APPLIED;
        }
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        Optional<ReferralCodeEntity> found = codes.findByCode(code).filter(c -> c.getRole() == role);
        if (found.isEmpty()) {
            return ApplyOutcome.INVALID_CODE;
        }
        UUID referrerId = found.get().getAccountId();
        if (authApi.findAccount(referrerId).map(AccountSummary::blocked).orElse(true)) {
            return ApplyOutcome.INVALID_CODE;
        }
        if (sameOwner(referrerId, refereeId)) {
            return ApplyOutcome.OWN_CODE;
        }
        if (!newEnough(refereeId, now)) {
            return ApplyOutcome.NOT_ELIGIBLE;
        }
        referrals.save(new ReferralEntity(referrerId, refereeId, role, code));
        log.info("Referral recorded: {} {} joined with {}'s code", role, refereeId, referrerId);
        return ApplyOutcome.APPLIED;
    }

    private boolean sameOwner(UUID a, UUID b) {
        return authApi.samePerson(a, b) || devices.shareAnInstall(a, b);
    }

    /** Signed up within the window and never finished a trip - so the first paid one is really her first. */
    private boolean newEnough(UUID accountId, Instant now) {
        Optional<AccountSummary> account = authApi.findAccount(accountId);
        if (account.isEmpty() || account.get().createdAt() == null
                || account.get().createdAt().plus(applyWindow).isBefore(now)) {
            return false;
        }
        return bookingApi.getObject().findAllForAccount(accountId).stream()
                .noneMatch(b -> b.status() == BookingStatus.COMPLETED);
    }

    // ------------------------------------------------------------ her first paid trip

    /**
     * A trip was paid for. If the rider on it - or the partner who drove it -
     * joined with a code and is still waiting, this is her first paid trip.
     * After the payment commits, in a transaction of its own: a referral must
     * never be able to undo a payment.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPaymentCaptured(PaymentCaptured event) {
        Result<BookingParticipants, BookingError> participants = bookingApi.getObject().getParticipants(event.bookingId());
        if (participants.isFailure()) {
            return;
        }
        UUID riderId = participants.value().customerId();
        UUID partnerId = participants.value().driverId();
        // Hers only if she paid something herself: a trip her signup credit
        // paid in full is a trip, but not yet a paying one.
        boolean riderPaid = event.method() != PaymentMethod.PROMO_CREDIT
                && event.amount() != null && event.amount().signum() > 0;
        if (riderId != null && riderPaid) {
            referrals.findByRefereeAccountId(riderId)
                    .filter(r -> r.getRole() == AccountRole.CUSTOMER && r.getStatus() == ReferralEntity.Status.PENDING)
                    .ifPresent(r -> complete(r.getId(), event.bookingId()));
        }
        if (partnerId != null) {
            referrals.findByRefereeAccountId(partnerId)
                    .filter(r -> r.getRole() == AccountRole.DRIVER && r.getStatus() == ReferralEntity.Status.PENDING)
                    .ifPresent(r -> complete(r.getId(), event.bookingId()));
        }
    }

    void complete(UUID referralId, UUID bookingId) {
        ReferralEntity referral = referrals.findLockedById(referralId).orElse(null);
        if (referral == null || referral.getStatus() != ReferralEntity.Status.PENDING) {
            return;
        }
        Instant now = Instant.now();
        UUID referrer = referral.getReferrerAccountId();
        UUID referee = referral.getRefereeAccountId();
        if (sameOwner(referrer, referee)) {
            referral.reject(bookingId, "Same person: a shared phone number, Google email or app install", now);
            referrals.save(referral);
            log.warn("Referral {} rejected: referrer {} and referee {} look like one person", referralId, referrer, referee);
            return;
        }
        // Her row locked, so two friends finishing together are decided one after the other.
        codes.findLockedByAccountId(referrer);
        List<String> notes = new ArrayList<>();
        boolean partner = referral.getRole() == AccountRole.DRIVER;

        BigDecimal refereeReward = partner
                ? incentives.awardReferral(IncentiveType.REFERRAL_WELCOME, referee, bookingId, now)
                : promotions.grantReferralCredit(PromotionType.REFERRAL_WELCOME, referee, now);
        if (refereeReward.signum() == 0) notes.add("Welcome campaign not running or out of budget");

        BigDecimal referrerReward = BigDecimal.ZERO;
        if (authApi.findAccount(referrer).map(AccountSummary::blocked).orElse(true)) {
            notes.add("Referrer's account is blocked");
        } else if (referrals.countRewardedFor(referrer) >= maxRewardedPerReferrer) {
            notes.add("Referrer has reached the limit of " + maxRewardedPerReferrer + " rewarded referrals");
        } else {
            referrerReward = partner
                    ? incentives.awardReferral(IncentiveType.REFERRAL_REWARD, referrer, bookingId, now)
                    : promotions.grantReferralCredit(PromotionType.REFERRAL_REWARD, referrer, now);
            if (referrerReward.signum() == 0) notes.add("Reward campaign not running or out of budget");
        }

        referral.complete(bookingId, referrerReward, refereeReward, notes.isEmpty() ? null : String.join("; ", notes), now);
        referrals.save(referral);
        log.info("Referral {} completed on trip {}: referrer {} got {}, referee {} got {}",
                referralId, bookingId, referrer, referrerReward, referee, refereeReward);
    }
}
