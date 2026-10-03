package com.sheout.driververification.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.driververification.InsuranceUseType;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.driververification.internal.PartnerDocumentService;
import com.sheout.driververification.internal.PoliceVerificationService;
import com.sheout.driververification.internal.provider.BackgroundCheckService;
import com.sheout.driververification.internal.VerificationError;
import com.sheout.driververification.internal.VerificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    private final PartnerDocumentService partnerDocuments;
    private final BackgroundCheckService backgroundChecks;

    public AdminVerificationController(VerificationService verificationService, AuthApi authApi,
                                       PartnerDocumentService partnerDocuments, BackgroundCheckService backgroundChecks) {
        this.verificationService = verificationService;
        this.authApi = authApi;
        this.partnerDocuments = partnerDocuments;
        this.backgroundChecks = backgroundChecks;
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
                accountId, admin.accountId(), new PoliceVerificationService.PoliceDecision(request.decision(),
                        request.method(), request.certificateNumber(), request.issuingAuthority(), request.issuedOn(),
                        request.reverifyDueOn(), request.evidenceDocumentId(), request.extraEvidenceDocumentId(),
                        request.reason()));
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    // ------------------------------------------------------- partner documents

    /**
     * An operator's decision on one of her documents, with whatever they
     * corrected after reading it. Approving needs the evidence and the facts
     * that make it checkable; rejecting needs a reason - see
     * PartnerDocumentService.review.
     */
    @PostMapping("/api/v1/admin/verification/documents/{documentId}/review")
    public ResponseEntity<PartnerDocumentSummary> reviewDocument(@PathVariable UUID documentId,
                                                                 @Valid @RequestBody DocumentReviewRequest request) {
        CurrentAccount admin = requireAdmin();
        Result<PartnerDocumentSummary, VerificationError> result = partnerDocuments.review(documentId, admin.accountId(),
                request.decision(), request.reason(),
                new PartnerDocumentService.DocumentFacts(request.documentNumber(), request.issuedOn(),
                        request.validUntil(), request.insuranceUseType()));
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    /**
     * A document an operator puts on file for her - a certificate SheOut
     * obtained, or a background report. Goes to the same review as one she
     * uploaded, so a second pair of eyes is not skipped by the route it came in.
     */
    @PostMapping(value = "/api/v1/admin/verification/{accountId}/documents/{type}", consumes = "multipart/form-data")
    public ResponseEntity<PartnerDocumentSummary> uploadDocument(
            @PathVariable UUID accountId, @PathVariable PartnerDocumentType type,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "documentNumber", required = false) @Size(max = 64) String documentNumber,
            @RequestParam(value = "issuedOn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedOn,
            @RequestParam(value = "validUntil", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
            @RequestParam(value = "insuranceUseType", required = false) InsuranceUseType insuranceUseType) {
        CurrentAccount admin = requireAdmin();
        if (verificationService.findByAccountId(accountId).map(v -> v.role() != AccountRole.DRIVER).orElse(true)) {
            throw VerificationController.toApiException(VerificationError.NOT_A_PARTNER);
        }
        Result<PartnerDocumentSummary, VerificationError> result = partnerDocuments.submit(accountId, type,
                file == null || file.isEmpty() ? null : PartnerDocumentController.toUpload(file),
                new PartnerDocumentService.DocumentFacts(documentNumber, issuedOn, validUntil, insuranceUseType),
                PartnerDocumentSource.OPERATOR_UPLOAD, admin.accountId(), false);
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    /**
     * A short-lived link to one document's file. The view is written to her
     * audit trail before the link is returned, so every opening of a police
     * certificate - or any other document - has a name and a time on it.
     */
    @GetMapping("/api/v1/admin/verification/documents/{documentId}/link")
    public ResponseEntity<DocumentLink> documentLink(@PathVariable UUID documentId) {
        CurrentAccount admin = requireAdmin();
        return verificationService.openPartnerDocument(documentId, admin.accountId())
                .map(url -> ResponseEntity.ok(new DocumentLink(url)))
                .orElseThrow(() -> ApiException.notFound("No file for that document"));
    }

    public record DocumentLink(String url) {
    }

    /** Which verification provider is set up, so the console offers "Request a background check" only when one is. */
    @GetMapping("/api/v1/admin/verification/provider")
    public ResponseEntity<ProviderStatus> provider() {
        requireAdmin();
        return ResponseEntity.ok(new ProviderStatus(backgroundChecks.providerName(), backgroundChecks.providerConfigured()));
    }

    /**
     * Sends her to the configured provider for a background check. The
     * answer comes back as a report for an operator to read; it decides
     * nothing on its own. PROVIDER_NOT_CONFIGURED with the manual default.
     */
    @PostMapping("/api/v1/admin/verification/{accountId}/background-check")
    public ResponseEntity<PartnerDocumentSummary> requestBackgroundCheck(@PathVariable UUID accountId) {
        CurrentAccount admin = requireAdmin();
        Result<PartnerDocumentSummary, VerificationError> result =
                backgroundChecks.requestCheck(accountId, admin.accountId());
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    public record ProviderStatus(String name, boolean configured) {
    }

    /** Every field but the decision optional: they are corrections, applied before the rules are checked. */
    public record DocumentReviewRequest(
            @NotNull PartnerDocumentStatus decision,
            @Size(max = 1000) String reason,
            @Size(max = 64) String documentNumber,
            LocalDate issuedOn,
            LocalDate validUntil,
            InsuranceUseType insuranceUseType) {
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

    /** reason is stored in rejection_reason, varchar(1000); longer failed at the database as a 500. */
    public record ReviewRequest(@NotNull VerificationStatus decision, @Size(max = 1000) String reason) {
    }

    /**
     * VERIFIED needs method, certificateNumber, issuedOn and
     * evidenceDocumentId (a partner_documents id); reverifyDueOn defaults to
     * issuedOn + POLICE_REVERIFY_MONTHS. REJECTED needs reason. The server
     * enforces both, whatever the console allows.
     */
    public record PoliceReviewRequest(
            @NotNull VerificationStatus decision,
            com.sheout.driververification.PoliceVerificationMethod method,
            @Size(max = 64) String certificateNumber,
            @Size(max = 200) String issuingAuthority,
            LocalDate issuedOn,
            LocalDate reverifyDueOn,
            UUID evidenceDocumentId,
            UUID extraEvidenceDocumentId,
            @Size(max = 1000) String reason) {
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
