package com.sheout.campaigns.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.campaigns.internal.ReferralService;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Refer a Friend, for riders and partners alike - see ReferralService.
 * <p>
 * X-Install-Id is a random id each app keeps in its own storage, sent on
 * these two calls only. It is how a friend applying a code from the phone the
 * code was shared from is recognised as its owner.
 */
@RestController
public class ReferralController {

    private final ReferralService referrals;
    private final RateLimiter rateLimiter;

    public ReferralController(ReferralService referrals, RateLimiter rateLimiter) {
        this.referrals = referrals;
        this.rateLimiter = rateLimiter;
    }

    /** Her code, her link, and how her referrals are going. */
    @GetMapping("/api/v1/referrals/me")
    public ResponseEntity<ReferralService.ReferralSummary> mine(
            @RequestHeader(name = "X-Install-Id", required = false) String installId) {
        CurrentAccount caller = requireRiderOrPartner();
        return ResponseEntity.ok(referrals.summary(caller.accountId(), caller.role(), installId));
    }

    /**
     * A new account enters a friend's code. Attempts are limited, and every
     * code that cannot be used for a reason outside her own account gives
     * the same answer, so codes cannot be discovered here.
     */
    @PostMapping("/api/v1/referrals/apply")
    public ResponseEntity<ApplyResponse> apply(@Valid @RequestBody ApplyRequest request,
                                      @RequestHeader(name = "X-Install-Id", required = false) String installId) {
        CurrentAccount caller = requireRiderOrPartner();
        rateLimiter.tryConsume("referral-apply:" + caller.accountId(), 10, Duration.ofHours(1))
                .orThrow("Too many codes tried. Please wait a while and try again.");
        ReferralService.ApplyResult result = referrals.apply(caller.accountId(), caller.role(), request.code(), installId);
        return switch (result.outcome()) {
            case APPLIED -> ResponseEntity.ok(new ApplyResponse(result.referrerFirstName(), result.reward(), result.cashReward()));
            case INVALID_CODE -> throw new ApiException(HttpStatus.NOT_FOUND, "REFERRAL_CODE_INVALID",
                    "That referral code isn't valid. Check it with the friend who sent it.");
            case OWN_CODE -> throw new ApiException(HttpStatus.CONFLICT, "REFERRAL_OWN_CODE",
                    "You can't use your own referral code.");
            case ALREADY_APPLIED -> throw new ApiException(HttpStatus.CONFLICT, "REFERRAL_ALREADY_APPLIED",
                    "You have already joined with a referral code.");
            case NOT_ELIGIBLE -> throw new ApiException(HttpStatus.CONFLICT, "REFERRAL_NOT_ELIGIBLE",
                    "Referral codes are for new accounts, entered while signing up.");
        };
    }

    public record ApplyRequest(@NotBlank @Size(max = 20) String code) {
    }

    /** What her welcome screen says: who invited her and what her first paid trip brings. */
    public record ApplyResponse(String referrerFirstName, BigDecimal reward, boolean cashReward) {
    }

    private static CurrentAccount requireRiderOrPartner() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER && caller.role() != AccountRole.DRIVER) {
            throw ApiException.forbidden("Referrals are for riders and partners");
        }
        return caller;
    }
}
