package com.sheout.driververification.internal.web;

import com.sheout.staff.Approvals;
import com.sheout.staff.RequiresStepUp;
import com.sheout.staff.AuditedRead;
import com.sheout.staff.Permission;
import com.sheout.staff.WorkAssignments;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.StaffContext;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
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
import com.sheout.driververification.internal.VerificationCheckService;
import com.sheout.driververification.internal.ReviewOversightService;
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
    private final WorkAssignments work;
    private final VerificationCheckService checks;
    private final ReviewOversightService oversight;
    private final Approvals approvals;

    public AdminVerificationController(VerificationService verificationService, AuthApi authApi,
                                       PartnerDocumentService partnerDocuments, BackgroundCheckService backgroundChecks,
                                       WorkAssignments work, VerificationCheckService checks, ReviewOversightService oversight,
                                       Approvals approvals) {
        this.checks = checks;
        this.oversight = oversight;
        this.approvals = approvals;
        this.work = work;
        this.verificationService = verificationService;
        this.authApi = authApi;
        this.partnerDocuments = partnerDocuments;
        this.backgroundChecks = backgroundChecks;
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @GetMapping("/api/v1/admin/verification/queue")
    public ResponseEntity<List<QueueItem>> queue(
            @RequestParam(name = "status", defaultValue = "UNDER_REVIEW") VerificationStatus status) {
        List<QueueItem> items = verificationService.findByGenderStatus(status).stream()
                .map(this::toQueueItem)
                .toList();
        return ResponseEntity.ok(work.visibleQueue(items, QueueItem::accountId, WorkAssignments.Kind.VERIFICATION,
                Permission.VERIFICATION_ALL));
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/api/v1/admin/verification/{accountId}/gender-review")
    public ResponseEntity<VerificationSummary> reviewGender(@PathVariable UUID accountId,
                                                              @Valid @RequestBody ReviewRequest request) {
        CurrentAccount admin = caller();
        mayOpen(accountId);
        requireCodeWhenRejecting(request.decision() == VerificationStatus.REJECTED, request.rejectionCode());
        Result<VerificationSummary, VerificationError> result = verificationService.reviewGenderVerification(
                accountId, admin.accountId(), request.decision(), request.reason(), request.acknowledged(),
                request.rejectionCode(), request.note());
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    @RequiresPermission(Permission.VERIFICATION_POLICE_RECORD)
    @PostMapping("/api/v1/admin/verification/{accountId}/police-review")
    public ResponseEntity<VerificationSummary> reviewPolice(@PathVariable UUID accountId,
                                                              @Valid @RequestBody PoliceReviewRequest request) {
        CurrentAccount admin = caller();
        mayOpen(accountId);
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
    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/api/v1/admin/verification/documents/{documentId}/review")
    public ResponseEntity<PartnerDocumentSummary> reviewDocument(@PathVariable UUID documentId,
                                                                 @Valid @RequestBody DocumentReviewRequest request) {
        CurrentAccount admin = caller();
        mayOpenDocument(documentId);
        requireCodeWhenRejecting(request.decision() == PartnerDocumentStatus.REJECTED, request.rejectionCode());
        Result<PartnerDocumentSummary, VerificationError> result = partnerDocuments.review(documentId, admin.accountId(),
                request.decision(), request.reason(),
                new PartnerDocumentService.DocumentFacts(request.documentNumber(), request.issuedOn(),
                        request.validUntil(), request.insuranceUseType()),
                request.governmentCheck(), request.pucValidUntil(), request.acknowledged(), request.rejectionCode(),
                request.note());
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
    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping(value = "/api/v1/admin/verification/{accountId}/documents/{type}", consumes = "multipart/form-data")
    public ResponseEntity<PartnerDocumentSummary> uploadDocument(
            @PathVariable UUID accountId, @PathVariable PartnerDocumentType type,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "documentNumber", required = false) @Size(max = 64) String documentNumber,
            @RequestParam(value = "issuedOn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedOn,
            @RequestParam(value = "validUntil", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
            @RequestParam(value = "insuranceUseType", required = false) InsuranceUseType insuranceUseType) {
        CurrentAccount admin = caller();
        mayOpen(accountId);
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
    @RequiresPermission(Permission.DOCUMENTS_VIEW)
    @RequiresStepUp
    @AuditedRead("documents.view")
    @GetMapping("/api/v1/admin/verification/documents/{documentId}/link")
    public ResponseEntity<DocumentLink> documentLink(@PathVariable UUID documentId) {
        CurrentAccount admin = caller();
        mayOpenDocument(documentId);
        return verificationService.openPartnerDocument(documentId, admin.accountId())
                .map(url -> ResponseEntity.ok(new DocumentLink(url)))
                .orElseThrow(() -> ApiException.notFound("No file for that document"));
    }

    public record DocumentLink(String url) {
    }

    /** Which verification provider is set up, so the console offers "Request a background check" only when one is. */
    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @GetMapping("/api/v1/admin/verification/provider")
    public ResponseEntity<ProviderStatus> provider() {
        return ResponseEntity.ok(new ProviderStatus(backgroundChecks.providerName(), backgroundChecks.providerConfigured()));
    }

    /**
     * Sends her to the configured provider for a background check. The
     * answer comes back as a report for an operator to read; it decides
     * nothing on its own. PROVIDER_NOT_CONFIGURED with the manual default.
     */
    @RequiresPermission(Permission.VERIFICATION_POLICE_RECORD)
    @PostMapping("/api/v1/admin/verification/{accountId}/background-check")
    public ResponseEntity<PartnerDocumentSummary> requestBackgroundCheck(@PathVariable UUID accountId) {
        CurrentAccount admin = caller();
        mayOpen(accountId);
        Result<PartnerDocumentSummary, VerificationError> result =
                backgroundChecks.requestCheck(accountId, admin.accountId());
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    /**
     * An agent works only the partners she has taken from the queue; managers
     * and owners (verification.all) any. See staff.WorkAssignments.
     */
    /**
     * Every check on her, worked out again now - so a duplicate that arrived
     * on another account since she was last opened shows up here.
     */
    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @GetMapping("/api/v1/admin/verification/{accountId}/checks")
    public ResponseEntity<com.sheout.driververification.VerificationChecks> checks(@PathVariable UUID accountId) {
        mayOpen(accountId);
        return ResponseEntity.ok(verificationService.checksFor(accountId));
    }

    private void mayOpen(UUID accountId) {
        work.requireMayOpen(WorkAssignments.Kind.VERIFICATION, accountId, Permission.VERIFICATION_ALL);
    }

    private void mayOpenDocument(UUID documentId) {
        partnerDocuments.find(documentId).ifPresent(document -> mayOpen(document.accountId()));
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
            InsuranceUseType insuranceUseType,
            /** What the government record (Parivahan) showed, in the operator's words; kept on the audit trail. */
            @Size(max = 300) String governmentCheck,
            /** With an RC: the PUC validity the Vahan record shows; files her PUC without an upload. */
            LocalDate pucValidUntil,
            /** What the automatic checks noticed that the reviewer ticked as looked at. Approving needs all of them. */
            java.util.Set<com.sheout.driververification.CheckFinding> acknowledged,
            /** Sending it back: the reason from the list. She is shown its message. */
            com.sheout.driververification.RejectionReason rejectionCode,
            /** For staff only, on the audit trail; never shown to her. */
            @Size(max = 500) String note) {
    }

    /**
     * Who is acting, for the records that say who decided. Whether she may is
     * already settled: the endpoint's permission was checked before it ran
     * (staff's StaffPermissionInterceptor).
     */
    private static CurrentAccount caller() {
        return CurrentAccountContext.get().orElseThrow(StaffContext::signInRequired);
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
    public record ReviewRequest(@NotNull VerificationStatus decision, @Size(max = 1000) String reason,
                                java.util.Set<com.sheout.driververification.CheckFinding> acknowledged,
                                com.sheout.driververification.RejectionReason rejectionCode,
                                @Size(max = 500) String note) {
    }

    /**
     * Sending something back takes a reason from the list, so she is told
     * how to fix it in her own language. Enforced here, at the console's
     * door; the services still take a typed reason for their own callers.
     */
    private static void requireCodeWhenRejecting(boolean rejecting, com.sheout.driververification.RejectionReason code) {
        if (rejecting && code == null) {
            throw VerificationController.toApiException(VerificationError.REASON_CODE_REQUIRED);
        }
    }

    // ------------------------------------------------------------- reasons list

    /** The reasons that may be given for her ID (no documentType) or for one document, with what she is shown. */
    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @GetMapping("/api/v1/admin/verification/rejection-reasons")
    public ResponseEntity<List<ReasonOption>> rejectionReasons(
            @RequestParam(name = "documentType", required = false) com.sheout.driververification.PartnerDocumentType documentType) {
        return ResponseEntity.ok(com.sheout.driververification.RejectionReason.forTarget(documentType).stream()
                .map(r -> new ReasonOption(r.name(), r.message(documentType), r.remembersNumber())).toList());
    }

    public record ReasonOption(String code, String message, boolean remembersNumber) {
    }

    // ---------------------------------------------------------------- overrides

    /**
     * Asks for a failed check to be accepted anyway. Nothing changes until a
     * second person, holding verification.all, signs it off on the Approvals
     * page; the note is what they read before they do.
     */
    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/api/v1/admin/verification/{accountId}/checks/{type}/override")
    public ResponseEntity<Approvals.Submitted> askOverride(@PathVariable UUID accountId,
                                                           @PathVariable com.sheout.driververification.CheckType type,
                                                           @Valid @RequestBody OverrideAsk request) {
        CurrentAccount admin = caller();
        mayOpen(accountId);
        String target = accountId + ":" + type;
        if (approvals.pending(Approvals.Kind.VERIFICATION_CHECK_OVERRIDE, target)) {
            throw VerificationController.toApiException(VerificationError.OVERRIDE_ALREADY_ASKED);
        }
        Result<VerificationCheckService.OverrideRequest, VerificationError> prepared =
                checks.prepareOverride(accountId, type, request.note(), admin.accountId());
        if (prepared.isFailure()) {
            throw VerificationController.toApiException(prepared.error());
        }
        String who = authApi.findAccount(accountId).map(a -> a.phoneNumber()).orElse(accountId.toString());
        return ResponseEntity.accepted().body(approvals.submit(new Approvals.Request(Approvals.Kind.VERIFICATION_CHECK_OVERRIDE,
                "Accept the failed " + type.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " check for " + who,
                Approvals.payload(prepared.value()), null, Permission.VERIFICATION_ALL, "VERIFICATION_CHECK", target,
                prepared.value().note())));
    }

    public record OverrideAsk(@NotNull @Size(min = 10, max = 1000) String note) {
    }

    /** Carries out an override, as the second person who signed it off. */
    @org.springframework.context.annotation.Bean
    static com.sheout.staff.ApprovalExecutor verificationCheckOverrides(VerificationCheckService checks) {
        return new com.sheout.staff.ApprovalExecutor() {
            @Override
            public Approvals.Kind kind() {
                return Approvals.Kind.VERIFICATION_CHECK_OVERRIDE;
            }

            @Override
            public Outcome execute(String payload) {
                VerificationCheckService.OverrideRequest request = Approvals.read(payload, VerificationCheckService.OverrideRequest.class);
                Result<com.sheout.driververification.VerificationCheck, VerificationError> result =
                        checks.applyOverride(request, StaffContext.requireSignedIn().accountId());
                if (result.isFailure()) {
                    return Outcome.failed(VerificationController.toApiException(result.error()).getMessage());
                }
                return Outcome.done("The " + request.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                        + " check is accepted. A reviewer can now approve the document.");
            }
        };
    }

    // ------------------------------------------------------------- oversight

    /** This week's (or the given week's) 10% sample of decided cases, for a second look. */
    @RequiresPermission(Permission.VERIFICATION_ALL)
    @GetMapping("/api/v1/admin/verification/sample")
    public ResponseEntity<ReviewOversightService.Sample> sample(@RequestParam(name = "week", required = false) String week) {
        Result<ReviewOversightService.Sample, VerificationError> result =
                oversight.weeklySample(week == null || week.isBlank() ? ReviewOversightService.currentWeek() : week.trim());
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    @RequiresPermission(Permission.VERIFICATION_ALL)
    @PostMapping("/api/v1/admin/verification/sample/{accountId}/second-look")
    public ResponseEntity<ReviewOversightService.SecondLook> secondLook(@PathVariable UUID accountId,
                                                                        @Valid @RequestBody SecondLookRequest request) {
        Result<ReviewOversightService.SecondLook, VerificationError> result = oversight.recordSecondLook(accountId,
                request.week(), caller().accountId(), request.outcome(), request.note());
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    public record SecondLookRequest(@NotNull String week, @NotNull String outcome, @Size(max = 1000) String note) {
    }

    /** Each reviewer's decisions and override rate over the last days (30 by default). */
    @RequiresPermission(Permission.VERIFICATION_ALL)
    @GetMapping("/api/v1/admin/verification/reviewer-stats")
    public ResponseEntity<List<ReviewOversightService.ReviewerStats>> reviewerStats(
            @RequestParam(name = "days", defaultValue = "30") int days) {
        return ResponseEntity.ok(oversight.reviewerStats(days));
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
