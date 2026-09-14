package com.sheout.privacy.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.privacy.internal.DataExport;
import com.sheout.privacy.internal.PrivacyFacade;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The account holder's own data rights. Self-service only: the account acted
 * on is always the caller's, taken from the token - there is no id to pass.
 */
@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyController {

    /** What the account holder must type to delete. Checked here too, not only in the app. */
    private static final String CONFIRMATION_WORD = "DELETE";

    private final PrivacyFacade privacy;
    private final RateLimiter rateLimiter;

    PrivacyController(PrivacyFacade privacy, RateLimiter rateLimiter) {
        this.privacy = privacy;
        this.rateLimiter = rateLimiter;
    }

    /**
     * A copy of everything held about the caller, as a JSON file download.
     * <p>
     * Limited to a handful an hour per account: it is the heaviest read in
     * the app - every trip, payment, rating and ticket - and nobody needs
     * their whole history more than a few times in an hour.
     */
    @GetMapping("/export")
    public ResponseEntity<DataExport> export() {
        CurrentAccount caller = requireRiderOrPartner();
        rateLimiter.tryConsume("privacy-export:" + caller.accountId(), 5, Duration.ofHours(1))
                .orThrow("You have downloaded your data several times in the last hour. Please try again later.");
        DataExport export = privacy.export(caller.accountId(), caller.role())
                .orElseThrow(() -> ApiException.notFound("No account found"));
        String filename = "sheout-my-data-" + LocalDate.now(ZoneOffset.UTC) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                // A file someone saved stays theirs; an intermediary must not keep a copy.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(export);
    }

    /**
     * Deletes the caller's account - see AccountDeletionService for what that
     * means and what is retained. The body must carry the word DELETE, so a
     * mistaken or scripted call cannot do this by posting nothing.
     */
    @PostMapping("/delete-account")
    public ResponseEntity<DeletionResponse> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        CurrentAccount caller = requireRiderOrPartner();
        if (!CONFIRMATION_WORD.equals(request.confirmation())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONFIRMATION_REQUIRED",
                    "Type DELETE to confirm you want to delete your account.");
        }
        PrivacyFacade.DeletionOutcome outcome = privacy.deleteAccount(caller.accountId(), caller.role());
        if (!outcome.deleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_TRIP",
                    "You have a trip that is still under way. Finish or cancel it, then delete your account.");
        }
        return ResponseEntity.ok(new DeletionResponse(outcome.requestedAt(), outcome.completedAt()));
    }

    /** A role gate, so 403. Operations accounts are not deleted from the apps. */
    private CurrentAccount requireRiderOrPartner() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER && caller.role() != AccountRole.DRIVER) {
            throw ApiException.forbidden("Data rights requests are made from the rider and partner apps");
        }
        return caller;
    }

    public record DeleteAccountRequest(@NotNull String confirmation) {
    }

    public record DeletionResponse(Instant requestedAt, Instant completedAt) {
    }
}
