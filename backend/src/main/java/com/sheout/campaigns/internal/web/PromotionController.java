package com.sheout.campaigns.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.campaigns.internal.PromotionService;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** A rider's own promotions: what she holds, and entering a code. */
@RestController
public class PromotionController {

    private final PromotionService promotions;
    private final RateLimiter rateLimiter;

    public PromotionController(PromotionService promotions, RateLimiter rateLimiter) {
        this.promotions = promotions;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/api/v1/promotions/me")
    public ResponseEntity<List<PromotionService.HeldPromotion>> mine() {
        return ResponseEntity.ok(promotions.heldBy(requireRider().accountId()));
    }

    /**
     * Unknown, paused, ended and spent codes all give the same answer, and
     * attempts are limited, so the endpoint cannot be used to discover which
     * codes exist.
     */
    @PostMapping("/api/v1/promotions/redeem")
    public ResponseEntity<Void> redeem(@Valid @RequestBody RedeemRequest request) {
        CurrentAccount rider = requireRider();
        rateLimiter.tryConsume("promo-redeem:" + rider.accountId(), 10, Duration.ofHours(1))
                .orThrow("Too many codes tried. Please wait a while and try again.");
        return switch (promotions.redeemCode(rider.accountId(), request.code())) {
            case REDEEMED -> ResponseEntity.noContent().build();
            case ALREADY_REDEEMED -> throw new ApiException(HttpStatus.CONFLICT, "PROMO_ALREADY_REDEEMED",
                    "You have already added this code.");
            case UNKNOWN_OR_ENDED -> throw new ApiException(HttpStatus.NOT_FOUND, "PROMO_NOT_FOUND",
                    "That code is not valid, or the offer has ended.");
        };
    }

    public record RedeemRequest(@NotBlank @Size(max = 40) String code) {
    }

    private static CurrentAccount requireRider() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER) {
            throw ApiException.forbidden("Promotions are for riders");
        }
        return caller;
    }
}
