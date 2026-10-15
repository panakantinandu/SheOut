package com.sheout.driververification.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.driververification.ApplicantProfiles;
import com.sheout.driververification.CourtRejectionCategory;
import com.sheout.driververification.CourtScreeningDecision;
import com.sheout.driververification.CourtScreeningStatus;
import com.sheout.driververification.CourtSearchOutcome;
import com.sheout.driververification.internal.CourtScreeningEntity;
import com.sheout.driververification.internal.CourtScreeningError;
import com.sheout.driververification.internal.CourtScreeningSearchEntity;
import com.sheout.driververification.internal.CourtScreeningService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.AuditedRead;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.StaffContext;
import com.sheout.staff.WorkAssignments;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The console's court-record screening box, for reviewers
 * (verification.review). Every rule is in CourtScreeningService; this maps
 * requests and answers. While ADMIN_COURT_SCREEN_ENABLED is off every
 * endpoint answers 404, so the feature cannot be found, let alone used.
 * <p>
 * A role without verification.review gets the console's usual 403
 * (PERMISSION_REQUIRED) from the permission check, before this is reached.
 */
@RestController
@RequestMapping("/api/v1/admin/court-screenings")
public class AdminCourtScreeningController {

    /** eCourts' own party-name search, opened in a new tab. Nothing is fetched or submitted from here. */
    static final String ECOURTS_PARTY_SEARCH = "https://services.ecourts.gov.in/ecourtindia_v6/?p=casestatus/index";

    private final CourtScreeningService service;
    private final ObjectProvider<ApplicantProfiles> profiles;
    private final WorkAssignments work;

    public AdminCourtScreeningController(CourtScreeningService service, ObjectProvider<ApplicantProfiles> profiles,
                                         WorkAssignments work) {
        this.service = service;
        this.profiles = profiles;
        this.work = work;
    }

    public record SearchView(UUID id, String district, String court, String nameVariant, LocalDate searchedOn, UUID reviewerId,
                             CourtSearchOutcome outcome, String note, String caseType, String caseStatus, Integer caseYear,
                             boolean hasScreenshot, Instant at) {
        static SearchView of(CourtScreeningSearchEntity s) {
            return new SearchView(s.getId(), s.getDistrict(), s.getCourt(), s.getNameVariant(), s.getSearchedOn(), s.getReviewerId(),
                    s.getOutcome(), s.getNote(), s.getCaseType(), s.getCaseStatus(), s.getCaseYear(), s.getScreenshotKey() != null,
                    s.getCreatedAt());
        }
    }

    public record ScreeningView(UUID id, UUID accountId, CourtScreeningStatus status, String applicantName, LocalDate dateOfBirth,
                                String consentVersion, Instant consentAt, List<String> nameVariants, String relativeName,
                                List<String> districts, UUID requestedBy, UUID flaggedBy, UUID secondReviewer,
                                String secondOutcome, String secondNote, Instant partnerNotifiedAt, Instant responseDueAt,
                                String partnerResponse, Instant partnerRespondedAt, CourtScreeningDecision decision,
                                CourtRejectionCategory reasonCategory, String decisionNote, UUID decidedBy, Instant decidedAt,
                                LocalDate pccIssuedOn, String pccAuthority, LocalDate pccValidUntil, Instant anonymisedAt,
                                List<SearchView> searches, String eCourtsSearchUrl, String disclaimer) {
    }

    private ScreeningView view(CourtScreeningService.Screening s) {
        CourtScreeningEntity e = s.screening();
        ApplicantProfiles source = profiles.getIfAvailable();
        ApplicantProfiles.ApplicantProfile p = source == null ? null : source.find(e.getAccountId()).orElse(null);
        return new ScreeningView(e.getId(), e.getAccountId(), e.getStatus(), p == null ? null : p.name(),
                p == null ? null : p.dateOfBirth(), e.getConsentVersion(), e.getConsentAt(), e.getNameVariants(),
                e.getRelativeName(), e.getDistricts(), e.getRequestedBy(), e.getFlaggedBy(), e.getSecondReviewer(),
                e.getSecondOutcome(), e.getSecondNote(), e.getPartnerNotifiedAt(), e.getResponseDueAt(), e.getPartnerResponse(),
                e.getPartnerRespondedAt(), e.getDecision(), e.getReasonCategory(), e.getDecisionNote(), e.getDecidedBy(),
                e.getDecidedAt(), e.getPccIssuedOn(), e.getPccAuthority(), e.getPccValidUntil(), e.getAnonymisedAt(),
                s.searches().stream().map(SearchView::of).toList(), ECOURTS_PARTY_SEARCH,
                "A screening aid, not police verification. Never tell anyone a partner is police or background verified because of it.");
    }

    public record QueueRow(UUID accountId, CourtScreeningStatus status, Instant updatedAt, Instant responseDueAt) {
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @GetMapping
    public ResponseEntity<List<QueueRow>> queue() {
        List<CourtScreeningEntity> rows = unwrap(service.queue());
        return ResponseEntity.ok(work.visibleQueue(
                rows.stream().map(r -> new QueueRow(r.getAccountId(), r.getStatus(), r.getUpdatedAt(), r.getResponseDueAt())).toList(),
                QueueRow::accountId, WorkAssignments.Kind.VERIFICATION, Permission.VERIFICATION_ALL));
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @GetMapping("/report")
    public ResponseEntity<CourtScreeningService.Report> report() {
        return ResponseEntity.ok(unwrap(service.report()));
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @AuditedRead("verification.court_screen.view")
    @GetMapping("/{accountId}")
    public ResponseEntity<ScreeningView> get(@PathVariable UUID accountId) {
        mayOpen(accountId);
        return ResponseEntity.ok(view(unwrap(service.open(accountId, me()))));
    }

    public record RequestScreening(@Size(max = 5) List<@Size(max = 100) String> extraDistricts) {
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/{accountId}")
    public ResponseEntity<ScreeningView> request(@PathVariable UUID accountId, @Valid @RequestBody(required = false) RequestScreening body) {
        mayOpen(accountId);
        return ResponseEntity.ok(view(unwrap(service.request(accountId, me(), body == null ? null : body.extraDistricts()))));
    }

    public record Subject(@Size(max = 10) List<@Size(max = 150) String> nameVariants, @Size(max = 150) String relativeName,
                          @Size(max = 10) List<@Size(max = 100) String> districts) {
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PutMapping("/{accountId}/subject")
    public ResponseEntity<ScreeningView> subject(@PathVariable UUID accountId, @Valid @RequestBody Subject body) {
        mayOpen(accountId);
        return ResponseEntity.ok(view(unwrap(service.updateSubject(accountId, me(), body.nameVariants(), body.relativeName(),
                body.districts()))));
    }

    public record Search(@NotNull @Size(max = 100) String district, @Size(max = 150) String court,
                         @NotNull @Size(max = 150) String nameVariant, LocalDate searchedOn, @NotNull CourtSearchOutcome outcome,
                         @Size(max = 1000) String note, @Size(max = 100) String caseType, @Size(max = 30) String caseStatus,
                         Integer caseYear) {
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/{accountId}/searches")
    public ResponseEntity<SearchView> logSearch(@PathVariable UUID accountId, @Valid @RequestBody Search body) {
        mayOpen(accountId);
        return ResponseEntity.ok(SearchView.of(unwrap(service.logSearch(accountId, me(), new CourtScreeningService.SearchEntry(
                body.district(), body.court(), body.nameVariant(), body.searchedOn(), body.outcome(), body.note(),
                body.caseType(), body.caseStatus(), body.caseYear())))));
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/{accountId}/searches/{searchId}/screenshot")
    public ResponseEntity<SearchView> screenshot(@PathVariable UUID accountId, @PathVariable UUID searchId,
                                                 @RequestParam("file") MultipartFile file) throws IOException {
        mayOpen(accountId);
        DocumentUpload upload = new DocumentUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return ResponseEntity.ok(SearchView.of(unwrap(service.attachScreenshot(accountId, me(), searchId, upload))));
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @AuditedRead("documents.view")
    @GetMapping("/{accountId}/searches/{searchId}/screenshot")
    public ResponseEntity<Map<String, String>> screenshotUrl(@PathVariable UUID accountId, @PathVariable UUID searchId) {
        mayOpen(accountId);
        return ResponseEntity.ok(Map.of("url", unwrap(service.screenshotUrl(accountId, me(), searchId))));
    }

    public record SignOff(@NotNull Boolean agree, @NotNull @Size(min = 1, max = 1000) String note) {
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/{accountId}/second-signoff")
    public ResponseEntity<ScreeningView> secondSignOff(@PathVariable UUID accountId, @Valid @RequestBody SignOff body) {
        mayOpen(accountId);
        return ResponseEntity.ok(view(unwrap(service.secondSignOff(accountId, me(), body.agree(), body.note()))));
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/{accountId}/notify")
    public ResponseEntity<ScreeningView> notifyPartner(@PathVariable UUID accountId) {
        mayOpen(accountId);
        return ResponseEntity.ok(view(unwrap(service.notifyPartner(accountId, me()))));
    }

    public record DecisionBody(@NotNull CourtScreeningDecision decision, CourtRejectionCategory reasonCategory,
                               @Size(max = 1000) String note, LocalDate pccIssuedOn, @Size(max = 200) String pccAuthority,
                               LocalDate pccValidUntil, UUID pccDocumentId) {
    }

    @RequiresPermission(Permission.VERIFICATION_REVIEW)
    @PostMapping("/{accountId}/decision")
    public ResponseEntity<ScreeningView> decide(@PathVariable UUID accountId, @Valid @RequestBody DecisionBody body) {
        mayOpen(accountId);
        return ResponseEntity.ok(view(unwrap(service.decide(accountId, me(), new CourtScreeningService.Decision(body.decision(),
                body.reasonCategory(), body.note(), body.pccIssuedOn(), body.pccAuthority(), body.pccValidUntil(),
                body.pccDocumentId())))));
    }

    // -------------------------------------------------------------- helpers

    private void mayOpen(UUID accountId) {
        work.requireMayOpen(WorkAssignments.Kind.VERIFICATION, accountId, Permission.VERIFICATION_ALL);
    }

    private static UUID me() {
        CurrentAccount caller = CurrentAccountContext.get().orElseThrow(StaffContext::signInRequired);
        return caller.accountId();
    }

    static <T> T unwrap(Result<T, CourtScreeningError> result) {
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return result.value();
    }

    static ApiException toApiException(CourtScreeningError error) {
        return switch (error) {
            case NOT_FOUND -> ApiException.notFound("Not found");
            case NOT_A_PARTNER -> new ApiException(HttpStatus.BAD_REQUEST, error.name(), "Only partners are screened.");
            case ALREADY_OPEN -> new ApiException(HttpStatus.CONFLICT, error.name(), "A screening is already open for her.");
            case CONSENT_REQUIRED -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "She has not agreed to this search yet. Nothing may be searched before she does.");
            case CONSENT_VERSION_OUTDATED -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "The wording changed. Please read it again and agree to the new version.");
            case WRONG_STATE -> new ApiException(HttpStatus.CONFLICT, error.name(), "That step does not apply at this point.");
            case NOTE_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, error.name(), "Write a note.");
            case SEARCH_INCOMPLETE -> new ApiException(HttpStatus.BAD_REQUEST, error.name(),
                    "Say which district you searched, under which name, and what you found.");
            case SAME_REVIEWER -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "A second reviewer must sign this off: someone other than the reviewer who flagged it.");
            case OWN_FLAGGED_CASE -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "You flagged this case, so another reviewer must decide it.");
            case SECOND_SIGNOFF_REQUIRED -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "A possible match needs a second reviewer's sign-off first.");
            case PARTNER_NOT_HEARD -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "She must be told and given until the response date to answer before a rejection.");
            case REASON_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, error.name(), "Choose a reason category.");
            case NO_COMPLETED_SEARCH -> new ApiException(HttpStatus.CONFLICT, error.name(),
                    "Approval needs at least one completed search, or a police clearance certificate.");
            case PCC_DETAILS_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, error.name(),
                    "Record the certificate's date and the issuing authority.");
            case SCREENSHOT_INVALID -> new ApiException(HttpStatus.BAD_REQUEST, error.name(),
                    "Send a JPEG, PNG or WebP image of up to 5 MB.");
        };
    }
}
