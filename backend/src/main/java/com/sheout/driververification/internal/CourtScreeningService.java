package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;
import com.sheout.driververification.CourtRejectionCategory;
import com.sheout.driververification.CourtScreeningDecision;
import com.sheout.driververification.CourtScreeningStatus;
import com.sheout.driververification.CourtSearchOutcome;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentRules;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manual court-record screening: a person searches public court records
 * (eCourts) for a partner, by hand, and writes down every search.
 * <p>
 * A SCREENING AID, NOT POLICE VERIFICATION. Nothing here sets a partner's
 * police status, and no screen or message ever calls her "police verified"
 * or "background verified" because of it. The decision is an input to the
 * reviewer's police decision, recorded beside it.
 * <p>
 * NO AUTOMATION. Nothing is scraped, no captcha is touched, nothing is
 * submitted to a government site: the console opens eCourts in a new tab
 * and the reviewer types what she saw.
 * <p>
 * THE RULES, all enforced here:
 * <ul>
 *   <li>Off unless ADMIN_COURT_SCREEN_ENABLED=true; while off, every call
 *       answers NOT_FOUND.</li>
 *   <li>Nothing is searched before the partner agrees to this search, to the
 *       wording in force (COURT_SCREEN_CONSENT_VERSION). If she withdraws,
 *       the screening stops.</li>
 *   <li>Every search outcome other than "No match" needs a note.</li>
 *   <li>A possible or confirmed match needs a second reviewer's sign-off,
 *       from someone other than the reviewer who flagged it.</li>
 *   <li>Nobody decides a case they flagged themselves.</li>
 *   <li>Before any rejection, she is told and given a response window
 *       (COURT_SCREEN_RESPONSE_DAYS) to explain or send her own police
 *       clearance certificate. A rejection keeps a reason category, not the
 *       case.</li>
 *   <li>An approval needs at least one completed search, and is written as a
 *       COURT_RECORD_SCREEN check decided by that reviewer, so the weekly
 *       10% second-look sample covers it.</li>
 *   <li>Every step - including opening the screening - is in the
 *       verification audit trail (and the staff audit log).</li>
 * </ul>
 */
@Service
public class CourtScreeningService {

    private static final int MAX_SCREENSHOT_BYTES = 5 * 1024 * 1024;
    private static final EnumSet<CourtScreeningStatus> SEARCHABLE = EnumSet.of(
            CourtScreeningStatus.SEARCHING, CourtScreeningStatus.RESPONSE_RECEIVED, CourtScreeningStatus.AWAITING_PCC);

    private final CourtScreeningRepository screenings;
    private final CourtScreeningSearchRepository searches;
    private final VerificationRecordRepository records;
    private final VerificationCheckRepository checks;
    private final VerificationAudit audit;
    private final DocumentStorage storage;
    private final boolean enabled;
    private final String consentVersion;
    private final Duration responseWindow;
    private final List<String> defaultDistricts;
    private Clock clock = Clock.systemUTC();

    public CourtScreeningService(CourtScreeningRepository screenings, CourtScreeningSearchRepository searches,
                                 VerificationRecordRepository records, VerificationCheckRepository checks,
                                 VerificationAudit audit, DocumentStorage storage,
                                 @Value("${sheout.verification.court-screen.enabled:false}") boolean enabled,
                                 @Value("${sheout.verification.court-screen.consent-version:2026-10-14-draft}") String consentVersion,
                                 @Value("${sheout.verification.court-screen.response-days:7}") int responseDays,
                                 @Value("${sheout.verification.court-screen.districts:Hyderabad,Rangareddy,Medchal-Malkajgiri}") String districts) {
        this.screenings = screenings;
        this.searches = searches;
        this.records = records;
        this.checks = checks;
        this.audit = audit;
        this.storage = storage;
        this.enabled = enabled;
        this.consentVersion = consentVersion;
        this.responseWindow = Duration.ofDays(responseDays);
        this.defaultDistricts = Arrays.stream(districts.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** For tests: a fixed clock. */
    void useClock(Clock clock) {
        this.clock = clock;
    }

    public boolean enabled() {
        return enabled;
    }

    public String consentVersion() {
        return consentVersion;
    }

    public record Screening(CourtScreeningEntity screening, List<CourtScreeningSearchEntity> searches) {
    }

    // ------------------------------------------------------------- reviewer

    /** The latest screening for a partner, with its searches. Recorded as a view. */
    @Transactional
    public Result<Screening, CourtScreeningError> open(UUID accountId, UUID reviewer) {
        if (!enabled) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        Optional<CourtScreeningEntity> latest = screenings.findByAccountIdOrderByCreatedAtDesc(accountId).stream().findFirst();
        if (latest.isEmpty()) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.COURT_SCREEN_VIEWED, null, null, null);
        return Result.success(withSearches(latest.get()));
    }

    /** A reviewer asks for a screening. She is asked to agree before anything is searched. */
    @Transactional
    public Result<Screening, CourtScreeningError> request(UUID accountId, UUID reviewer, List<String> extraDistricts) {
        if (!enabled) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        Optional<VerificationRecordEntity> record = records.findByAccountId(accountId);
        if (record.isEmpty()) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        if (record.get().getRole() != AccountRole.DRIVER) {
            return Result.failure(CourtScreeningError.NOT_A_PARTNER);
        }
        if (screenings.findOpen(accountId).isPresent()) {
            return Result.failure(CourtScreeningError.ALREADY_OPEN);
        }
        List<String> districts = new ArrayList<>(defaultDistricts);
        if (extraDistricts != null) {
            districts.addAll(extraDistricts);
        }
        CourtScreeningEntity saved = screenings.save(new CourtScreeningEntity(accountId, reviewer, districts));
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.COURT_SCREEN_REQUESTED, null, null, null);
        return Result.success(withSearches(saved));
    }

    /** The names and districts to search under. Reviewers add spellings and transliterations. */
    @Transactional
    public Result<Screening, CourtScreeningError> updateSubject(UUID accountId, UUID reviewer, List<String> nameVariants,
                                                              String relativeName, List<String> districts) {
        Result<CourtScreeningEntity, CourtScreeningError> found = openFor(accountId);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        CourtScreeningEntity s = found.value();
        s.subject(nameVariants, relativeName, districts);
        screenings.save(s);
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.COURT_SCREEN_SUBJECT_UPDATED, null, null, null);
        return Result.success(withSearches(s));
    }

    public record SearchEntry(String district, String court, String nameVariant, LocalDate searchedOn,
                              CourtSearchOutcome outcome, String note, String caseType, String caseStatus, Integer caseYear) {
    }

    /** One search, written down. A possible or confirmed match sends the case to a second reviewer. */
    @Transactional
    public Result<CourtScreeningSearchEntity, CourtScreeningError> logSearch(UUID accountId, UUID reviewer, SearchEntry entry) {
        Result<CourtScreeningEntity, CourtScreeningError> found = openFor(accountId);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        CourtScreeningEntity s = found.value();
        if (s.getStatus() == CourtScreeningStatus.CONSENT_REQUESTED) {
            return Result.failure(CourtScreeningError.CONSENT_REQUIRED);
        }
        if (!SEARCHABLE.contains(s.getStatus()) && s.getStatus() != CourtScreeningStatus.MATCH_TO_CONFIRM) {
            return Result.failure(CourtScreeningError.WRONG_STATE);
        }
        if (blank(entry.district()) || blank(entry.nameVariant()) || entry.outcome() == null) {
            return Result.failure(CourtScreeningError.SEARCH_INCOMPLETE);
        }
        if (entry.outcome() != CourtSearchOutcome.NO_MATCH && blank(entry.note())) {
            return Result.failure(CourtScreeningError.NOTE_REQUIRED);
        }
        boolean match = entry.outcome().isMatch();
        CourtScreeningSearchEntity saved = searches.save(new CourtScreeningSearchEntity(s.getId(), entry.district().trim(),
                trim(entry.court()), entry.nameVariant().trim(),
                entry.searchedOn() == null ? LocalDate.now(clock) : entry.searchedOn(), reviewer, entry.outcome(),
                trim(entry.note()), match ? trim(entry.caseType()) : null, match ? trim(entry.caseStatus()) : null,
                match ? entry.caseYear() : null));
        if (match) {
            s.flag(reviewer);
            screenings.save(s);
        }
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.COURT_SCREEN_SEARCH_LOGGED, null, null,
                entry.outcome().name());
        return Result.success(saved);
    }

    /** A screenshot of what the reviewer saw, kept privately with the search. */
    @Transactional
    public Result<CourtScreeningSearchEntity, CourtScreeningError> attachScreenshot(UUID accountId, UUID reviewer, UUID searchId,
                                                                                  DocumentUpload upload) {
        Result<CourtScreeningEntity, CourtScreeningError> found = openFor(accountId);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        Optional<CourtScreeningSearchEntity> search = searches.findById(searchId)
                .filter(x -> x.getScreeningId().equals(found.value().getId()));
        if (search.isEmpty()) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        if (upload == null || upload.content() == null || upload.content().length > MAX_SCREENSHOT_BYTES
                || DocumentRules.photoTypeOf(upload.content()) == null) {
            return Result.failure(CourtScreeningError.SCREENSHOT_INVALID);
        }
        CourtScreeningSearchEntity row = search.get();
        if (row.getScreenshotKey() != null) {
            storage.delete(row.getScreenshotKey());
        }
        row.attachScreenshot(storage.store(accountId, "COURT_SCREENSHOT", upload));
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.COURT_SCREEN_SCREENSHOT_ADDED, null, null, null);
        return Result.success(searches.save(row));
    }

    /** A signed link to a search's screenshot, recorded as a document view. */
    @Transactional
    public Result<String, CourtScreeningError> screenshotUrl(UUID accountId, UUID reviewer, UUID searchId) {
        if (!enabled) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        Optional<CourtScreeningSearchEntity> search = searches.findById(searchId).filter(x -> x.getScreenshotKey() != null);
        Optional<CourtScreeningEntity> screening = search.flatMap(x -> screenings.findById(x.getScreeningId()))
                .filter(x -> x.getAccountId().equals(accountId));
        if (screening.isEmpty()) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.DOCUMENT_VIEWED, null, null,
                "court screening screenshot");
        return Result.success(storage.resolveUrl(search.get().getScreenshotKey()));
    }

    /** A second reviewer's word on a flagged match: it may be her (agree), or it is not (disagree). */
    @Transactional
    public Result<Screening, CourtScreeningError> secondSignOff(UUID accountId, UUID reviewer, boolean agree, String note) {
        Result<CourtScreeningEntity, CourtScreeningError> found = openFor(accountId);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        CourtScreeningEntity s = found.value();
        if (s.getStatus() != CourtScreeningStatus.MATCH_TO_CONFIRM) {
            return Result.failure(CourtScreeningError.WRONG_STATE);
        }
        if (reviewer.equals(s.getFlaggedBy())) {
            return Result.failure(CourtScreeningError.SAME_REVIEWER);
        }
        if (blank(note)) {
            return Result.failure(CourtScreeningError.NOTE_REQUIRED);
        }
        s.secondSignOff(reviewer, agree, note.trim(), clock.instant());
        screenings.save(s);
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.COURT_SCREEN_SECOND_SIGNOFF, null, null,
                agree ? "AGREE" : "DISAGREE");
        return Result.success(withSearches(s));
    }

    /** She is told a record may match her name and given until the response date to answer. */
    @Transactional
    public Result<Screening, CourtScreeningError> notifyPartner(UUID accountId, UUID reviewer) {
        Result<CourtScreeningEntity, CourtScreeningError> found = openFor(accountId);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        CourtScreeningEntity s = found.value();
        if (s.getStatus() != CourtScreeningStatus.MATCH_CONFIRMED) {
            return Result.failure(CourtScreeningError.WRONG_STATE);
        }
        Instant now = clock.instant();
        s.notifyPartner(now, now.plus(responseWindow));
        screenings.save(s);
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.COURT_SCREEN_PARTNER_NOTIFIED, null, null, null);
        return Result.success(withSearches(s));
    }

    public record Decision(CourtScreeningDecision decision, CourtRejectionCategory reasonCategory, String note,
                           LocalDate pccIssuedOn, String pccAuthority, LocalDate pccValidUntil, UUID pccDocumentId) {
    }

    /** The decision - or a request for a police clearance certificate, which keeps the case open. */
    @Transactional
    public Result<Screening, CourtScreeningError> decide(UUID accountId, UUID reviewer, Decision d) {
        Result<CourtScreeningEntity, CourtScreeningError> found = openFor(accountId);
        if (found.isFailure()) {
            return Result.failure(found.error());
        }
        CourtScreeningEntity s = found.value();
        if (d == null || d.decision() == null) {
            return Result.failure(CourtScreeningError.WRONG_STATE);
        }
        if (s.getStatus() == CourtScreeningStatus.CONSENT_REQUESTED) {
            return Result.failure(CourtScreeningError.CONSENT_REQUIRED);
        }
        boolean flagged = s.getFlaggedBy() != null;
        if (flagged && reviewer.equals(s.getFlaggedBy())) {
            return Result.failure(CourtScreeningError.OWN_FLAGGED_CASE);
        }
        if (s.getStatus() == CourtScreeningStatus.MATCH_TO_CONFIRM) {
            return Result.failure(CourtScreeningError.SECOND_SIGNOFF_REQUIRED);
        }
        List<CourtScreeningSearchEntity> done = searches.findByScreeningIdOrderByCreatedAtAsc(s.getId());
        Instant now = clock.instant();
        switch (d.decision()) {
            case ASK_FOR_PCC -> {
                if (s.getStatus() == CourtScreeningStatus.AWAITING_PCC) {
                    return Result.failure(CourtScreeningError.WRONG_STATE);
                }
                s.askForPcc(now, now.plus(responseWindow));
            }
            case APPROVE, APPROVE_WITH_NOTE -> {
                boolean completedSearch = done.stream().anyMatch(x -> x.getOutcome() != CourtSearchOutcome.COULD_NOT_SEARCH);
                boolean pcc = d.pccIssuedOn() != null && !blank(d.pccAuthority());
                if (!completedSearch && !pcc) {
                    return Result.failure(CourtScreeningError.NO_COMPLETED_SEARCH);
                }
                if (s.getStatus() == CourtScreeningStatus.AWAITING_PCC && !pcc) {
                    return Result.failure(CourtScreeningError.PCC_DETAILS_REQUIRED);
                }
                if (d.decision() == CourtScreeningDecision.APPROVE_WITH_NOTE && blank(d.note())) {
                    return Result.failure(CourtScreeningError.NOTE_REQUIRED);
                }
                if (pcc) {
                    s.recordPcc(d.pccIssuedOn(), d.pccAuthority().trim(), d.pccValidUntil(), d.pccDocumentId());
                }
                s.decide(d.decision(), null, trim(d.note()), reviewer, now);
                recordCheck(accountId, CheckStatus.APPROVED_BY_PERSON, "Court-record screening approved", reviewer, now);
            }
            case REJECT -> {
                if (d.reasonCategory() == null) {
                    return Result.failure(CourtScreeningError.REASON_REQUIRED);
                }
                // Never without telling her first: she answered, or her window closed.
                boolean heard = s.getPartnerRespondedAt() != null
                        || (s.getResponseDueAt() != null && !now.isBefore(s.getResponseDueAt()));
                if (!heard) {
                    return Result.failure(CourtScreeningError.PARTNER_NOT_HEARD);
                }
                s.decide(CourtScreeningDecision.REJECT, d.reasonCategory(), trim(d.note()), reviewer, now);
                // The check has no rejected status: a person turning somebody down
                // is recorded here and in the audit trail, the check back to pending.
                recordCheck(accountId, CheckStatus.PENDING, "Court-record screening: not cleared", reviewer, now);
            }
        }
        screenings.save(s);
        audit.record(accountId, reviewer, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.COURT_SCREEN_DECIDED, null, null,
                d.decision().name() + (d.reasonCategory() == null ? "" : " " + d.reasonCategory().name()));
        return Result.success(withSearches(s));
    }

    // -------------------------------------------------------------- partner

    /** What the partner sees: her open screening (or the last one), or nothing when the feature is off. */
    @Transactional(readOnly = true)
    public Optional<CourtScreeningEntity> forPartner(UUID accountId) {
        if (!enabled) {
            return Optional.empty();
        }
        return screenings.findByAccountIdOrderByCreatedAtDesc(accountId).stream().findFirst();
    }

    /** She agrees to the search, to the wording she was shown. */
    @Transactional
    public Result<CourtScreeningEntity, CourtScreeningError> consent(UUID accountId, String version) {
        Optional<CourtScreeningEntity> open = enabled ? screenings.findOpen(accountId) : Optional.empty();
        if (open.isEmpty()) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        CourtScreeningEntity s = open.get();
        if (s.getStatus() != CourtScreeningStatus.CONSENT_REQUESTED) {
            return Result.failure(CourtScreeningError.WRONG_STATE);
        }
        if (!consentVersion.equals(version)) {
            return Result.failure(CourtScreeningError.CONSENT_VERSION_OUTDATED);
        }
        s.consent(version, clock.instant());
        audit.record(accountId, accountId, VerificationAudit.ACTOR_PARTNER, VerificationAudit.COURT_SCREEN_CONSENTED, null, null, version);
        return Result.success(screenings.save(s));
    }

    /** She withdraws: the search stops. What was already written stays under the retention rules. */
    @Transactional
    public Result<CourtScreeningEntity, CourtScreeningError> withdraw(UUID accountId) {
        Optional<CourtScreeningEntity> open = enabled ? screenings.findOpen(accountId) : Optional.empty();
        if (open.isEmpty()) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        CourtScreeningEntity s = open.get();
        s.withdraw();
        audit.record(accountId, accountId, VerificationAudit.ACTOR_PARTNER, VerificationAudit.COURT_SCREEN_WITHDRAWN, null, null, null);
        return Result.success(screenings.save(s));
    }

    /** Her answer after being told: in her own words. */
    @Transactional
    public Result<CourtScreeningEntity, CourtScreeningError> respond(UUID accountId, String text) {
        Optional<CourtScreeningEntity> open = enabled ? screenings.findOpen(accountId) : Optional.empty();
        if (open.isEmpty()) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        CourtScreeningEntity s = open.get();
        if (s.getStatus() != CourtScreeningStatus.AWAITING_RESPONSE) {
            return Result.failure(CourtScreeningError.WRONG_STATE);
        }
        if (blank(text)) {
            return Result.failure(CourtScreeningError.NOTE_REQUIRED);
        }
        s.partnerResponded(text.trim().substring(0, Math.min(text.trim().length(), 1000)), clock.instant());
        audit.record(accountId, accountId, VerificationAudit.ACTOR_PARTNER, VerificationAudit.COURT_SCREEN_PARTNER_RESPONDED, null, null, null);
        return Result.success(screenings.save(s));
    }

    // ------------------------------------------------------------- reports

    public record Report(long total, Map<CourtScreeningStatus, Long> byStatus, long flagged, long awaitingSecondSignOff,
                         long responseOverdue, Double averageHoursConsentToDecision) {
    }

    @Transactional(readOnly = true)
    public Result<Report, CourtScreeningError> report() {
        if (!enabled) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        List<CourtScreeningEntity> all = screenings.findAll();
        Map<CourtScreeningStatus, Long> byStatus = new EnumMap<>(CourtScreeningStatus.class);
        all.forEach(s -> byStatus.merge(s.getStatus(), 1L, Long::sum));
        Instant now = clock.instant();
        double hours = all.stream().filter(s -> s.getDecidedAt() != null && s.getConsentAt() != null)
                .mapToDouble(s -> Duration.between(s.getConsentAt(), s.getDecidedAt()).toMinutes() / 60.0).average().orElse(Double.NaN);
        return Result.success(new Report(all.size(), byStatus,
                all.stream().filter(s -> s.getFlaggedBy() != null).count(),
                all.stream().filter(s -> s.getStatus() == CourtScreeningStatus.MATCH_TO_CONFIRM).count(),
                all.stream().filter(s -> s.getStatus() == CourtScreeningStatus.AWAITING_RESPONSE
                        && s.getResponseDueAt() != null && s.getResponseDueAt().isBefore(now)).count(),
                Double.isNaN(hours) ? null : Math.round(hours * 10) / 10.0));
    }

    /** Open screenings, oldest first, for the console's queue. */
    @Transactional(readOnly = true)
    public Result<List<CourtScreeningEntity>, CourtScreeningError> queue() {
        if (!enabled) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        return Result.success(screenings.findByStatusInOrderByUpdatedAtAsc(EnumSet.of(
                CourtScreeningStatus.CONSENT_REQUESTED, CourtScreeningStatus.SEARCHING, CourtScreeningStatus.MATCH_TO_CONFIRM,
                CourtScreeningStatus.MATCH_CONFIRMED, CourtScreeningStatus.AWAITING_RESPONSE, CourtScreeningStatus.RESPONSE_RECEIVED,
                CourtScreeningStatus.AWAITING_PCC)));
    }

    // ----------------------------------------------------------- retention

    /** Account deletion: remember when she left; the retention task anonymises later. */
    @Transactional
    public void markLeft(UUID accountId) {
        Instant now = clock.instant();
        for (CourtScreeningEntity s : screenings.findByAccountIdOrderByCreatedAtDesc(accountId)) {
            s.markLeft(now);
            screenings.save(s);
        }
    }

    /** Rejected screenings decided before the cutoff. Counts only when dryRun. */
    @Transactional
    public int anonymiseRejectedBefore(Instant cutoff, boolean dryRun, int limit) {
        return anonymise(screenings.findRejectedToAnonymise(CourtScreeningStatus.REJECTED, cutoff, PageRequest.of(0, limit)), dryRun);
    }

    /** Screenings of partners who left before the cutoff. Counts only when dryRun. */
    @Transactional
    public int anonymiseDepartedBefore(Instant cutoff, boolean dryRun, int limit) {
        return anonymise(screenings.findDepartedToAnonymise(cutoff, PageRequest.of(0, limit)), dryRun);
    }

    private int anonymise(List<CourtScreeningEntity> due, boolean dryRun) {
        if (dryRun) {
            return due.size();
        }
        Instant now = clock.instant();
        for (CourtScreeningEntity s : due) {
            for (CourtScreeningSearchEntity search : searches.findByScreeningIdOrderByCreatedAtAsc(s.getId())) {
                if (search.getScreenshotKey() != null) {
                    storage.delete(search.getScreenshotKey());
                }
                search.anonymise();
                searches.save(search);
            }
            s.anonymise(now);
            screenings.save(s);
            audit.record(s.getAccountId(), null, VerificationAudit.ACTOR_SYSTEM, VerificationAudit.COURT_SCREEN_ANONYMISED, null, null, null);
        }
        return due.size();
    }

    // ------------------------------------------------------------- helpers

    private Result<CourtScreeningEntity, CourtScreeningError> openFor(UUID accountId) {
        if (!enabled) {
            return Result.failure(CourtScreeningError.NOT_FOUND);
        }
        return screenings.findOpen(accountId)
                .<Result<CourtScreeningEntity, CourtScreeningError>>map(Result::success)
                .orElseGet(() -> Result.failure(CourtScreeningError.NOT_FOUND));
    }

    private Screening withSearches(CourtScreeningEntity s) {
        return new Screening(s, searches.findByScreeningIdOrderByCreatedAtAsc(s.getId()));
    }

    /** The decision as a check row, so the weekly second-look sample includes it. */
    private void recordCheck(UUID accountId, CheckStatus status, String reason, UUID by, Instant at) {
        VerificationCheckEntity row = checks.findByAccountIdAndType(accountId, CheckType.COURT_RECORD_SCREEN)
                .orElseGet(() -> new VerificationCheckEntity(accountId, CheckType.COURT_RECORD_SCREEN, at));
        row.decide(status, reason, by, VerificationAudit.ACTOR_OPERATOR, at);
        checks.save(row);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }
}
