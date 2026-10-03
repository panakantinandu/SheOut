package com.sheout.driververification.internal.web;

import com.sheout.staff.WorkAssignments;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.StaffContext;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.driververification.internal.ShiftCheckError;
import com.sheout.driververification.internal.ShiftCheckService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The partner's start-of-shift safety check, and the operator's review of
 * the ones that did not match. See ShiftCheckService.
 */
@RestController
public class ShiftCheckController {

    /**
     * Attempts a day. An honest partner in bad light needs three or four;
     * the cap is on how many photos one account can push into storage.
     */
    private static final int ATTEMPTS_PER_DAY = 20;

    private final ShiftCheckService service;
    private final RateLimiter rateLimiter;
    private final AuthApi authApi;

    private final WorkAssignments work;

    public ShiftCheckController(ShiftCheckService service, RateLimiter rateLimiter, AuthApi authApi, WorkAssignments work) {
        this.work = work;
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.authApi = authApi;
    }

    @GetMapping("/api/v1/driver-verification/shift-check")
    public ResponseEntity<ShiftCheckService.Status> status() {
        CurrentAccount caller = requirePartner();
        return ResponseEntity.ok(service.statusFor(caller.accountId()));
    }

    @PostMapping("/api/v1/driver-verification/shift-check/challenge")
    public ResponseEntity<ShiftCheckService.Challenge> challenge() {
        CurrentAccount caller = requirePartner();
        rateLimiter.tryConsume("shift-check-challenge:" + caller.accountId(), ATTEMPTS_PER_DAY * 2, Duration.ofDays(1))
                .orThrow("You have started the selfie check many times today. Please contact support.");
        return ResponseEntity.ok(orThrow(service.issueChallenge(caller.accountId())));
    }

    @PostMapping(value = "/api/v1/driver-verification/shift-check", consumes = "multipart/form-data")
    public ResponseEntity<ShiftCheckService.Status> submit(
            @RequestParam("selfie") MultipartFile selfie,
            @RequestParam("livenessFrames") MultipartFile livenessFrames,
            @RequestParam(value = "helmet", required = false) MultipartFile helmet,
            @RequestParam("challengeId") String challengeId,
            @RequestParam(value = "faceDistance", required = false) Double faceDistance,
            @RequestParam(value = "faceOutcome", required = false) String faceOutcome) {
        CurrentAccount caller = requirePartner();
        rateLimiter.tryConsume("shift-check-submit:" + caller.accountId(), ATTEMPTS_PER_DAY, Duration.ofDays(1))
                .orThrow("You have tried the selfie check many times today. Please contact support.");
        return ResponseEntity.ok(orThrow(service.submit(caller.accountId(), challengeId,
                toUpload(selfie), toUpload(livenessFrames),
                helmet == null || helmet.isEmpty() ? null : toUpload(helmet),
                faceDistance, faceOutcome)));
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @GetMapping("/api/v1/admin/shift-checks")
    public ResponseEntity<List<QueueRow>> queue() {
        return ResponseEntity.ok(work.visibleQueue(service.reviewQueue(), ShiftCheckService.ReviewItem::accountId,
                        WorkAssignments.Kind.VERIFICATION, Permission.VERIFICATION_ALL).stream()
                .map(item -> new QueueRow(item, authApi.findAccount(item.accountId()).map(AccountSummary::phoneNumber).orElse(null)))
                .toList());
    }

    /** One check, with the phone number an operator will call her on. */
    public record QueueRow(ShiftCheckService.ReviewItem check, String phoneNumber) {
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/api/v1/admin/shift-checks/{checkId}/review")
    public ResponseEntity<ShiftCheckService.ReviewItem> review(@PathVariable UUID checkId,
                                                               @Valid @RequestBody ReviewRequest request) {
        CurrentAccount admin = caller();
        service.reviewQueue().stream().filter(item -> item.checkId().equals(checkId)).findFirst()
                .ifPresent(item -> work.requireMayOpen(WorkAssignments.Kind.VERIFICATION, item.accountId(), Permission.VERIFICATION_ALL));
        return ResponseEntity.ok(orThrow(service.review(checkId, admin.accountId(), request.decision(), request.note())));
    }

    public record ReviewRequest(@NotNull String decision, @Size(max = 500) String note) {
    }

    private static CurrentAccount requirePartner() {
        CurrentAccount caller = VerificationController.requireAuthenticated();
        if (caller.role() != AccountRole.DRIVER) {
            throw ApiException.forbidden("Only partners take the start-of-shift check");
        }
        return caller;
    }

    /**
     * Who is acting, for the records that say who decided. Whether she may is
     * already settled: the endpoint's permission was checked before it ran
     * (staff's StaffPermissionInterceptor).
     */
    private static CurrentAccount caller() {
        return CurrentAccountContext.get().orElseThrow(StaffContext::signInRequired);
    }

    private static <T> T orThrow(Result<T, ShiftCheckError> result) {
        if (result.isSuccess()) {
            return result.value();
        }
        throw switch (result.error()) {
            case CHALLENGE_EXPIRED -> new ApiException(HttpStatus.CONFLICT, "SHIFT_CHECK_EXPIRED",
                    "That selfie took too long. Please take it again.");
            case PHOTO_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "SELFIE_REQUIRED",
                    "Take the selfie with the in-app camera.");
            case UNDER_REVIEW -> new ApiException(HttpStatus.CONFLICT, "SHIFT_CHECK_UNDER_REVIEW",
                    "Our team is checking your selfie. You can go online once they have.");
            case STORAGE_FAILED -> new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Could not save the photo");
            case NOT_FOUND -> ApiException.notFound("No such check");
            case NOT_REVIEWABLE -> new ApiException(HttpStatus.CONFLICT, "Conflict", "This check has already been decided");
            case INVALID_DECISION -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Decision must be CLEAR or REJECT");
        };
    }

    private static DocumentUpload toUpload(MultipartFile file) {
        try {
            return new DocumentUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
