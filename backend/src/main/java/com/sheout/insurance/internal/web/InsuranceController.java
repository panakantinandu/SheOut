package com.sheout.insurance.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.insurance.CoverSummary;
import com.sheout.insurance.TripCover;
import com.sheout.insurance.internal.InsuranceClaimService;
import com.sheout.insurance.internal.InsuranceService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * What riders and partners see of insurance. Every answer is built from a
 * record that exists - a coverage row, a policy in force, an ENROLLED
 * membership - and an empty answer means the apps say nothing.
 */
@RestController
public class InsuranceController {

    private final InsuranceService insurance;
    private final InsuranceClaimService claims;
    private final RateLimiter rateLimiter;

    public InsuranceController(InsuranceService insurance, InsuranceClaimService claims, RateLimiter rateLimiter) {
        this.insurance = insurance;
        this.claims = claims;
        this.rateLimiter = rateLimiter;
    }

    /**
     * The "Insured trip" chip's answer for one of her trips: the cover, or
     * 404 when there is none she can be shown - no cover, or not her trip,
     * which answer the same so a trip id reveals nothing.
     */
    @GetMapping("/api/v1/insurance/trips/{bookingId}")
    public ResponseEntity<TripCover> tripCover(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireSignedIn();
        if (!insurance.isParticipant(bookingId, caller.accountId())) {
            throw ApiException.notFound("No cover for this trip");
        }
        return insurance.coverForTrip(bookingId).map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("No cover for this trip"));
    }

    /** "Report an accident / make a claim" - see InsuranceClaimService. */
    @PostMapping("/api/v1/insurance/trips/{bookingId}/claim")
    public ResponseEntity<InsuranceClaimService.ClaimResult> claim(@PathVariable UUID bookingId,
                                                                   @Valid @RequestBody ClaimRequest request) {
        CurrentAccount caller = requireSignedIn();
        rateLimiter.tryConsume("insurance-claim:" + caller.accountId(), 10, Duration.ofDays(1))
                .orThrow("You have reported several accidents today. Please call SheOut support if you need more help.");
        Result<InsuranceClaimService.ClaimResult, InsuranceClaimService.ClaimError> result =
                claims.raise(bookingId, caller.accountId(), caller.role(), request.description());
        if (result.isFailure()) {
            throw switch (result.error()) {
                case TRIP_NOT_FOUND -> ApiException.notFound("No such trip");
                case TRIP_NOT_STARTED -> new ApiException(HttpStatus.CONFLICT, "TRIP_NOT_STARTED",
                        "This trip never started, so there is no accident to report on it.");
                case DESCRIPTION_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "DESCRIPTION_REQUIRED",
                        "Tell us what happened, so the right person can help.");
            };
        }
        return ResponseEntity.ok(result.value());
    }

    /**
     * The passenger cover in force, for the Safety Center's "Every SheOut
     * trip is insured" - which the app shows only when active is true.
     */
    @GetMapping("/api/v1/insurance/passenger-cover")
    public ResponseEntity<PassengerCover> passengerCover() {
        requireSignedIn();
        return ResponseEntity.ok(insurance.currentPassengerCover()
                .map(c -> new PassengerCover(true, c))
                .orElse(new PassengerCover(false, null)));
    }

    /** A partner's group covers the insurer has confirmed. Empty until one is ENROLLED. */
    @GetMapping("/api/v1/insurance/me/covers")
    public ResponseEntity<List<CoverSummary>> myCovers() {
        CurrentAccount caller = requireSignedIn();
        if (caller.role() != AccountRole.DRIVER) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(insurance.enrolledCoversFor(caller.accountId()));
    }

    public record ClaimRequest(@NotBlank @Size(max = 1500) String description) {
    }

    public record PassengerCover(boolean active, CoverSummary cover) {
    }

    private static CurrentAccount requireSignedIn() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }
}
