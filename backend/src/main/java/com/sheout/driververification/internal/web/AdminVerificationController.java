package com.sheout.driververification.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.driververification.internal.VerificationError;
import com.sheout.driververification.internal.VerificationService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only. Authorization here is a role check against
 * {@link com.sheout.auth.CurrentAccountContext} - see the README for why
 * there's no real way to provision an ADMIN account yet (the admin module
 * doesn't exist), so today this only works against a row manually flipped
 * to ADMIN in the database.
 */
@RestController
public class AdminVerificationController {

    private final VerificationService verificationService;
    private final AuthApi authApi;

    public AdminVerificationController(VerificationService verificationService, AuthApi authApi) {
        this.verificationService = verificationService;
        this.authApi = authApi;
    }

    @GetMapping("/api/v1/admin/verification/queue")
    public ResponseEntity<List<QueueItem>> queue(
            @RequestParam(name = "status", defaultValue = "UNDER_REVIEW") VerificationStatus status) {
        requireAdmin();
        List<QueueItem> items = verificationService.findByGenderStatus(status).stream()
                .map(this::toQueueItem)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/api/v1/admin/verification/{accountId}/gender-review")
    public ResponseEntity<VerificationSummary> reviewGender(@PathVariable UUID accountId,
                                                              @Valid @RequestBody ReviewRequest request) {
        CurrentAccount admin = requireAdmin();
        Result<VerificationSummary, VerificationError> result = verificationService.reviewGenderVerification(
                accountId, admin.accountId(), request.decision(), request.reason());
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    @PostMapping("/api/v1/admin/verification/{accountId}/police-review")
    public ResponseEntity<VerificationSummary> reviewPolice(@PathVariable UUID accountId,
                                                              @Valid @RequestBody PoliceReviewRequest request) {
        CurrentAccount admin = requireAdmin();
        Result<VerificationSummary, VerificationError> result = verificationService.reviewPoliceVerification(
                accountId, admin.accountId(), request.decision());
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = VerificationController.requireAuthenticated();
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }

    private QueueItem toQueueItem(VerificationSummary summary) {
        String phoneNumber = authApi.findAccount(summary.accountId())
                .map(a -> a.phoneNumber())
                .orElse(null);
        return new QueueItem(
                summary.accountId(),
                phoneNumber,
                summary.role(),
                summary.genderVerificationStatus(),
                summary.policeVerificationStatus(),
                summary.documentSubmitted(),
                summary.updatedAt()
        );
    }

    public record ReviewRequest(@NotNull VerificationStatus decision, String reason) {
    }

    public record PoliceReviewRequest(@NotNull VerificationStatus decision) {
    }

    public record QueueItem(
            UUID accountId,
            String phoneNumber,
            AccountRole role,
            VerificationStatus genderVerificationStatus,
            VerificationStatus policeVerificationStatus,
            boolean documentSubmitted,
            java.time.Instant updatedAt
    ) {
    }
}
