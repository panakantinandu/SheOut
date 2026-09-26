package com.sheout.driververification.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.driververification.VerificationSummary;
import com.sheout.driververification.VerificationTurnaround;
import com.sheout.driververification.internal.VerificationFunnelStep;
import com.sheout.driververification.internal.VerificationError;
import com.sheout.driververification.internal.VerificationService;
import com.sheout.driververification.internal.LiveSelfieUpload;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

/**
 * Self-service endpoints - a caller only ever acts on their own record.
 * ASSUMPTION FLAGGED: uploading on someone else's behalf (e.g. an admin
 * submitting a document for a driver over the phone) isn't supported here -
 * not specified, and would need its own authorization rule if added.
 */
@RestController
public class VerificationController {

    /**
     * Uploads a day, per account. An honest retake after a blurry photo is
     * two or three; each upload is up to two 10 MB files, and without a cap
     * one account could fill the document store in minutes.
     */
    private static final int UPLOADS_PER_DAY = 10;

    private final VerificationService verificationService;
    private final RateLimiter rateLimiter;

    public VerificationController(VerificationService verificationService, RateLimiter rateLimiter) {
        this.verificationService = verificationService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(value = "/api/v1/driver-verification/documents", consumes = "multipart/form-data")
    public ResponseEntity<VerificationSummary> submitDocument(
            @RequestParam("file") MultipartFile file,
            // Optional on the wire, required for partners by the service. A
            // rider has no vehicle and cannot produce one; making it
            // mandatory here would break her submission to enforce a rule
            // that was never about her.
            @RequestParam(value = "rcFile", required = false) MultipartFile rcFile,
            // Optional on the wire so an app cached from before the selfie
            // gets SELFIE_REQUIRED, saying what is missing, not a bare 400.
            @RequestParam(value = "selfie", required = false) MultipartFile selfie,
            @RequestParam(value = "livenessFrames", required = false) MultipartFile livenessFrames,
            @RequestParam(value = "selfieChallengeId", required = false) String selfieChallengeId) {
        CurrentAccount caller = requireAuthenticated();
        rateLimiter.tryConsume("verification-upload:" + caller.accountId(), UPLOADS_PER_DAY, Duration.ofDays(1))
                .orThrow("You have uploaded documents several times today. Please try again tomorrow, or contact support.");
        DocumentUpload upload = toUpload(file);
        DocumentUpload rcUpload = rcFile == null || rcFile.isEmpty() ? null : toUpload(rcFile);
        LiveSelfieUpload live = new LiveSelfieUpload(
                selfie == null || selfie.isEmpty() ? null : toUpload(selfie),
                livenessFrames == null || livenessFrames.isEmpty() ? null : toUpload(livenessFrames),
                selfieChallengeId);
        Result<VerificationSummary, VerificationError> result =
                verificationService.submitDocument(caller.accountId(), upload, rcUpload, live);
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    /**
     * How long she is likely to wait, as the pending screen tells her.
     * <p>
     * Her own role's number: a rider's review and a partner's are different
     * jobs, and telling her about the other one would be telling her about
     * somebody else's queue.
     */
    /**
     * The prompts for her live selfie, and the id the submission must quote.
     * Asked for when she opens the camera; a new one replaces the last.
     * Counted against the same daily allowance as uploads, so it cannot be
     * called in a loop to fish for easy prompts.
     */
    @PostMapping("/api/v1/driver-verification/selfie-challenge")
    public ResponseEntity<VerificationService.SelfieChallenge> selfieChallenge() {
        CurrentAccount caller = requireAuthenticated();
        rateLimiter.tryConsume("selfie-challenge:" + caller.accountId(), UPLOADS_PER_DAY * 3, Duration.ofDays(1))
                .orThrow("You have started the selfie several times today. Please try again tomorrow, or contact support.");
        Result<VerificationService.SelfieChallenge, VerificationError> result =
                verificationService.issueSelfieChallenge(caller.accountId());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    @GetMapping("/api/v1/driver-verification/turnaround")
    public ResponseEntity<VerificationTurnaround> turnaround() {
        CurrentAccount caller = requireAuthenticated();
        return ResponseEntity.ok(verificationService.turnaroundFor(caller.role()));
    }

    /**
     * Notes that she reached the upload screen, or picked a document.
     * <p>
     * Fire-and-forget by design - 204 whatever happens, because a
     * measurement must never be the reason an upload screen shows an error.
     * Only ever about the caller's own account: there is no account id on
     * the wire to get wrong.
     */
    @PostMapping("/api/v1/driver-verification/progress/{step}")
    public ResponseEntity<Void> recordProgress(@PathVariable String step) {
        CurrentAccount caller = requireAuthenticated();
        VerificationFunnelStep parsed = VerificationFunnelStep.parse(step);
        if (parsed != null) {
            verificationService.recordFunnelStep(caller.accountId(), caller.role(), parsed);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/driver-verification/me")
    public ResponseEntity<VerificationSummary> getMyStatus() {
        CurrentAccount caller = requireAuthenticated();
        return verificationService.findByAccountId(caller.accountId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("No verification record found yet for this account"));
    }

    static CurrentAccount requireAuthenticated() {
        return CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    static ApiException toApiException(VerificationError error) {
        return switch (error) {
            case RECORD_NOT_FOUND -> ApiException.notFound("No verification record found for this account");
            case STORAGE_FAILED ->
                    new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Failed to store document");
            case NOT_UNDER_REVIEW ->
                    new ApiException(HttpStatus.CONFLICT, "Conflict", "Gender verification is not currently under review");
            case POLICE_VERIFICATION_NOT_APPLICABLE ->
                    new ApiException(HttpStatus.CONFLICT, "Conflict", "Police verification does not apply to this account");
            case INVALID_DECISION ->
                    new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Decision must be VERIFIED or REJECTED");
            case RC_DOCUMENT_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "RC_DOCUMENT_REQUIRED",
                    "Add a photo of your vehicle's registration certificate as well as your ID.");
            case DOCUMENT_TYPE_UNSUPPORTED -> new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_TYPE_UNSUPPORTED",
                    "Send a photo (JPG, PNG, HEIC or WebP) or a PDF. Other kinds of file cannot be opened for review.");
            case DOCUMENT_TOO_SMALL -> new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_TOO_SMALL",
                    "That file is too small to read. Photograph the whole document in good light, or send the original PDF.");
            case SELFIE_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "SELFIE_REQUIRED",
                    "Take a live selfie with the in-app camera before submitting.");
            case SELFIE_CHALLENGE_EXPIRED -> new ApiException(HttpStatus.CONFLICT, "SELFIE_CHALLENGE_EXPIRED",
                    "Your selfie has expired. Please take it again, then submit.");
            case ALREADY_VERIFIED -> new ApiException(HttpStatus.CONFLICT, "ALREADY_VERIFIED",
                    "Your ID is already verified. To change a document, contact support.");
            case DOCUMENT_TOO_LARGE -> new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_TOO_LARGE",
                    "That file is larger than 10 MB. A photo taken with your phone's camera will be well under it.");
        };
    }

    private DocumentUpload toUpload(MultipartFile file) {
        try {
            return new DocumentUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
