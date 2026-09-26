package com.sheout.admin.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingApi;
import com.sheout.campaigns.CampaignValidationException;
import com.sheout.campaigns.CampaignsAdminApi;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The console's Campaigns section: rider promotions and partner incentives,
 * what each has spent against its budget, and whether the signup credit
 * brings riders back at full price.
 */
@RestController
@RequestMapping("/api/v1/admin/campaigns")
public class AdminCampaignController {

    /** How long after a rider's credit ends we watch for her to book at full price. */
    static final Duration RETENTION_WINDOW = Duration.ofDays(30);

    private final CampaignsAdminApi campaigns;
    private final BookingApi bookings;
    private final AuthApi auth;

    public AdminCampaignController(CampaignsAdminApi campaigns, BookingApi bookings, AuthApi auth) {
        this.campaigns = campaigns;
        this.bookings = bookings;
        this.auth = auth;
    }

    public record Overview(List<CampaignsAdminApi.PromotionView> promotions, List<CampaignsAdminApi.IncentiveView> incentives) {
    }

    @GetMapping
    public ResponseEntity<Overview> overview() {
        requireAdmin();
        return ResponseEntity.ok(new Overview(campaigns.listPromotions(), campaigns.listIncentives()));
    }

    @PostMapping("/promotions")
    public ResponseEntity<CampaignsAdminApi.PromotionView> createPromotion(@RequestBody CampaignsAdminApi.PromotionDraft draft) {
        requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(validated(() -> campaigns.createPromotion(draft)));
    }

    @PutMapping("/promotions/{id}")
    public ResponseEntity<CampaignsAdminApi.PromotionView> updatePromotion(@PathVariable UUID id, @RequestBody CampaignsAdminApi.PromotionDraft draft) {
        requireAdmin();
        return ResponseEntity.ok(found(validated(() -> campaigns.updatePromotion(id, draft))));
    }

    @PostMapping("/promotions/{id}/pause")
    public ResponseEntity<CampaignsAdminApi.PromotionView> pausePromotion(@PathVariable UUID id) {
        requireAdmin();
        return ResponseEntity.ok(found(campaigns.setPromotionPaused(id, true)));
    }

    @PostMapping("/promotions/{id}/resume")
    public ResponseEntity<CampaignsAdminApi.PromotionView> resumePromotion(@PathVariable UUID id) {
        requireAdmin();
        return ResponseEntity.ok(found(campaigns.setPromotionPaused(id, false)));
    }

    @PostMapping("/incentives")
    public ResponseEntity<CampaignsAdminApi.IncentiveView> createIncentive(@RequestBody CampaignsAdminApi.IncentiveDraft draft) {
        requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(validated(() -> campaigns.createIncentive(draft)));
    }

    @PutMapping("/incentives/{id}")
    public ResponseEntity<CampaignsAdminApi.IncentiveView> updateIncentive(@PathVariable UUID id, @RequestBody CampaignsAdminApi.IncentiveDraft draft) {
        requireAdmin();
        return ResponseEntity.ok(found(validated(() -> campaigns.updateIncentive(id, draft))));
    }

    @PostMapping("/incentives/{id}/pause")
    public ResponseEntity<CampaignsAdminApi.IncentiveView> pauseIncentive(@PathVariable UUID id) {
        requireAdmin();
        return ResponseEntity.ok(found(campaigns.setIncentivePaused(id, true)));
    }

    @PostMapping("/incentives/{id}/resume")
    public ResponseEntity<CampaignsAdminApi.IncentiveView> resumeIncentive(@PathVariable UUID id) {
        requireAdmin();
        return ResponseEntity.ok(found(campaigns.setIncentivePaused(id, false)));
    }

    // ------------------------------------------------------------ Part D

    public record RetentionRow(UUID accountId, String phoneNumber, Instant grantedAt, BigDecimal creditUsed,
                               Instant endedAt, String endedBy, long fullPriceTrips, boolean windowComplete) {
    }

    /**
     * Did the signup credit create riders, or only free trips? For every
     * rider whose credit has ended - spent, or lapsed - the trips she then
     * paid for in full in the 30 days after.
     * <p>
     * The headline rate counts only riders whose 30 days are over: a rider
     * whose credit ran out yesterday has not had the chance to come back
     * yet, and counting her as "did not" would make the promotion look worse
     * than it is. Riders still inside their window are shown separately.
     */
    public record RetentionReport(long creditsGranted, long stillActive, long ended, long endedByUse, long endedByExpiry,
                                  long windowComplete, long cameBackAtFullPrice, BigDecimal cameBackRatePercent,
                                  BigDecimal averageFullPriceTrips, long windowOpen, long cameBackSoFar,
                                  List<RetentionRow> riders) {
    }

    @GetMapping("/signup-retention")
    public ResponseEntity<RetentionReport> signupRetention() {
        requireAdmin();
        Instant now = Instant.now();
        List<CampaignsAdminApi.SignupCreditOutcome> outcomes = campaigns.signupCreditOutcomes();
        List<RetentionRow> rows = outcomes.stream()
                .filter(o -> o.endedAt() != null)
                .map(o -> {
                    Instant windowEnd = o.endedAt().plus(RETENTION_WINDOW);
                    boolean complete = !now.isBefore(windowEnd);
                    long trips = bookings.countFullPriceTripsForCustomer(o.accountId(), o.endedAt(), complete ? windowEnd : now);
                    return new RetentionRow(o.accountId(), auth.findAccount(o.accountId()).map(a -> a.phoneNumber()).orElse(null),
                            o.grantedAt(), o.creditUsed(), o.endedAt(), o.endedBy(), trips, complete);
                })
                .sorted(Comparator.comparing(RetentionRow::endedAt).reversed())
                .toList();
        List<RetentionRow> complete = rows.stream().filter(RetentionRow::windowComplete).toList();
        List<RetentionRow> open = rows.stream().filter(r -> !r.windowComplete()).toList();
        long cameBack = complete.stream().filter(r -> r.fullPriceTrips() > 0).count();
        BigDecimal rate = complete.isEmpty() ? null
                : BigDecimal.valueOf(100.0 * cameBack / complete.size()).setScale(1, RoundingMode.HALF_UP);
        BigDecimal avg = complete.isEmpty() ? null
                : BigDecimal.valueOf(complete.stream().mapToLong(RetentionRow::fullPriceTrips).average().orElse(0))
                        .setScale(2, RoundingMode.HALF_UP);
        return ResponseEntity.ok(new RetentionReport(
                outcomes.size(),
                outcomes.stream().filter(o -> o.endedAt() == null).count(),
                rows.size(),
                rows.stream().filter(r -> "EXHAUSTED".equals(r.endedBy())).count(),
                rows.stream().filter(r -> "EXPIRED".equals(r.endedBy())).count(),
                complete.size(), cameBack, rate, avg,
                open.size(), open.stream().filter(r -> r.fullPriceTrips() > 0).count(),
                rows.stream().limit(200).toList()));
    }

    // ------------------------------------------------------------ shared

    private static <T> T validated(Supplier<T> action) {
        try {
            return action.get();
        } catch (CampaignValidationException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAMPAIGN_INVALID", e.getMessage());
        }
    }

    private static <T> T found(Optional<T> value) {
        return value.orElseThrow(() -> ApiException.notFound("No such campaign"));
    }

    private static CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }
}
