package com.sheout.driververification.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.driververification.InsuranceUseType;
import com.sheout.driververification.PartnerDocumentExpiring;
import com.sheout.driververification.PartnerDocumentRejected;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.PartnerReadiness;
import com.sheout.driververification.PartnerReadiness.Blocker;
import com.sheout.driververification.PartnerReadiness.BlockerCode;
import com.sheout.driververification.PartnerReadiness.DocumentState;
import com.sheout.driververification.VerificationLapsed;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.storage.DocumentRules;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A partner's licence, vehicle papers and police evidence: taking them in,
 * an operator's decision on each, whether they let her work today, and
 * expiring them on their printed date.
 * <p>
 * The decision on a document is always a person's. Nothing here reads a
 * date or a number off a photo: she types what she can, the operator checks
 * it against the image and corrects it, and only then is it approved. A
 * provider can be plugged in later (see VerificationProvider) without this
 * flow changing.
 * <p>
 * "In force" for a type is the current row - or, while a renewal she sent is
 * waiting for review, the earlier approved one it is replacing. Sending a
 * renewal early therefore never takes her off the road while it waits, and a
 * renewal that is turned down puts the earlier one back as current.
 */
@Service
public class PartnerDocumentService {

    private static final Logger log = LoggerFactory.getLogger(PartnerDocumentService.class);
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter PLAIN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final String META_INSURANCE_USE = "insuranceUseType";

    /** What she, an operator or a provider says is printed on the document. Every field optional on the way in. */
    public record DocumentFacts(String documentNumber, LocalDate issuedOn, LocalDate validUntil,
                                InsuranceUseType insuranceUseType) {

        public static DocumentFacts none() {
            return new DocumentFacts(null, null, null, null);
        }
    }

    private final PartnerDocumentRepository documents;
    private final DocumentStorage storage;
    private final DocumentRequirements requirements;
    private final VerificationAudit audit;
    private final DomainEventPublisher events;
    private final ObjectMapper json;
    private final VerificationConsent consent;
    private final Clock clock;

    @Autowired
    public PartnerDocumentService(PartnerDocumentRepository documents, DocumentStorage storage,
                                  DocumentRequirements requirements, VerificationAudit audit,
                                  DomainEventPublisher events, ObjectMapper json, VerificationConsent consent) {
        this(documents, storage, requirements, audit, events, json, consent, Clock.systemUTC());
    }

    PartnerDocumentService(PartnerDocumentRepository documents, DocumentStorage storage,
                           DocumentRequirements requirements, VerificationAudit audit,
                           DomainEventPublisher events, ObjectMapper json, VerificationConsent consent, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.requirements = requirements;
        this.audit = audit;
        this.events = events;
        this.json = json;
        this.consent = consent;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(INDIA));
    }

    // ------------------------------------------------------------------ intake

    /**
     * Files a document and puts it in front of an operator.
     * <p>
     * From her (PARTNER_UPLOAD) the printed facts are required where the
     * document has them - the number, the valid-until date, and for a policy
     * whether it is commercial - because an operator should be checking her
     * answer against the image, not transcribing it. withoutFacts is for the
     * RC that arrives with an older app's ID submission, which never asked.
     * <p>
     * The earlier row of the same type is superseded, not overwritten. If it
     * was approved and still valid it keeps counting until this one is
     * decided.
     */
    @Transactional
    public Result<PartnerDocumentSummary, VerificationError> submit(
            UUID accountId, PartnerDocumentType type, DocumentUpload upload, DocumentFacts facts,
            PartnerDocumentSource source, UUID actorId, boolean withoutFacts) {
        if (source == PartnerDocumentSource.PARTNER_UPLOAD && !type.partnerUploads()) {
            return Result.failure(VerificationError.DOCUMENT_TYPE_NOT_UPLOADABLE);
        }
        // Nothing is taken from her, or filed about her by an operator, until
        // she has agreed to be verified - and to the wording in force now. A
        // provider's report arrives only for a check her consent started.
        if (source != PartnerDocumentSource.PROVIDER && !consent.isCurrent(accountId)) {
            return Result.failure(VerificationError.CONSENT_REQUIRED);
        }
        if (upload == null) {
            return Result.failure(VerificationError.DOCUMENT_FILE_MISSING);
        }
        VerificationError badFile = fileProblem(upload);
        if (badFile != null) {
            return Result.failure(badFile);
        }
        DocumentFacts given = facts == null ? DocumentFacts.none() : facts;
        LocalDate today = today();
        if (!withoutFacts && source == PartnerDocumentSource.PARTNER_UPLOAD) {
            if (type.numbered() && blank(given.documentNumber())) {
                return Result.failure(VerificationError.DOCUMENT_NUMBER_REQUIRED);
            }
            if (type.expires() && given.validUntil() == null) {
                return Result.failure(VerificationError.VALID_UNTIL_REQUIRED);
            }
            if (type == PartnerDocumentType.VEHICLE_INSURANCE && given.insuranceUseType() == null) {
                return Result.failure(VerificationError.INSURANCE_USE_TYPE_REQUIRED);
            }
        }
        VerificationError badDates = dateProblem(given, today);
        if (badDates != null) {
            return Result.failure(badDates);
        }

        String key;
        try {
            key = storage.store(accountId, "partner-" + type.name().toLowerCase(Locale.ROOT), DocumentRules.asDetected(upload));
        } catch (RuntimeException ex) {
            return Result.failure(VerificationError.STORAGE_FAILED);
        }

        Instant now = clock.instant();
        Optional<PartnerDocumentEntity> previous = documents.findByAccountIdAndTypeAndSupersededAtIsNull(accountId, type);
        UUID replaces = null;
        if (previous.isPresent()) {
            PartnerDocumentEntity earlier = previous.get();
            replaces = stillCounts(earlier, today) ? earlier.getId()
                    // A second upload while the first is still waiting replaces
                    // the waiting one, and inherits what that one was replacing.
                    : earlier.getStatus() == PartnerDocumentStatus.UNDER_REVIEW
                            || earlier.getStatus() == PartnerDocumentStatus.PENDING
                            ? earlier.getReplacesDocumentId() : null;
            earlier.supersede(now);
            // Flushed now: Hibernate runs inserts before updates, and the new
            // row would otherwise meet this one in the one-current-row index.
            documents.saveAndFlush(earlier);
        }

        PartnerDocumentEntity document = new PartnerDocumentEntity(accountId, type, source,
                PartnerDocumentStatus.UNDER_REVIEW, now);
        document.setDocumentKey(key);
        applyFacts(document, given);
        document.setReplacesDocumentId(replaces);
        documents.save(document);
        audit.record(accountId, actorId, actorRoleFor(source), VerificationAudit.DOCUMENT_UPLOADED, document.getId(), type,
                source == PartnerDocumentSource.OPERATOR_UPLOAD ? "Uploaded by an operator on her behalf" : null);
        return Result.success(toSummary(document));
    }

    // ---------------------------------------------------------------- decision

    /**
     * An operator's decision on one document, with any correction to what
     * was typed.
     * <p>
     * Approving needs evidence on file and the facts that make it checkable:
     * the number on a numbered document, the valid-until date on one that
     * expires (and not already past), and a commercial-use policy for a
     * partner who carries passengers. Turning one down needs a reason, which
     * she is shown.
     */
    @Transactional
    public Result<PartnerDocumentSummary, VerificationError> review(
            UUID documentId, UUID operatorId, PartnerDocumentStatus decision, String reason, DocumentFacts corrections) {
        if (decision != PartnerDocumentStatus.VERIFIED && decision != PartnerDocumentStatus.REJECTED) {
            return Result.failure(VerificationError.INVALID_DECISION);
        }
        Optional<PartnerDocumentEntity> found = documents.findLockedById(documentId);
        if (found.isEmpty()) {
            return Result.failure(VerificationError.DOCUMENT_NOT_FOUND);
        }
        PartnerDocumentEntity document = found.get();
        if (!document.isCurrent() || (document.getStatus() != PartnerDocumentStatus.UNDER_REVIEW
                && document.getStatus() != PartnerDocumentStatus.PENDING)) {
            return Result.failure(VerificationError.DOCUMENT_NOT_UNDER_REVIEW);
        }
        LocalDate today = today();
        DocumentFacts given = corrections == null ? DocumentFacts.none() : corrections;
        VerificationError badDates = dateProblem(given, today);
        if (badDates != null && decision == PartnerDocumentStatus.VERIFIED) {
            return Result.failure(badDates);
        }
        Instant now = clock.instant();

        if (decision == PartnerDocumentStatus.REJECTED) {
            if (blank(reason)) {
                return Result.failure(VerificationError.REASON_REQUIRED);
            }
            document.markRejected(operatorId, now, reason.trim());
            audit.record(document.getAccountId(), operatorId, VerificationAudit.ACTOR_OPERATOR,
                    VerificationAudit.DOCUMENT_REJECTED, document.getId(), document.getType(), reason.trim());
            restoreEarlierIfStillValid(document, operatorId, now, today);
            events.publish(new PartnerDocumentRejected(document.getAccountId(), document.getType(), reason.trim()));
            return Result.success(toSummary(document));
        }

        applyFacts(document, given);
        PartnerDocumentType type = document.getType();
        if (document.getDocumentKey() == null) {
            return Result.failure(VerificationError.DOCUMENT_FILE_MISSING);
        }
        if (type.numbered() && blank(document.getDocumentNumber())) {
            return Result.failure(VerificationError.DOCUMENT_NUMBER_REQUIRED);
        }
        if (type.expires() && document.getValidUntil() == null) {
            return Result.failure(VerificationError.VALID_UNTIL_REQUIRED);
        }
        if (document.getValidUntil() != null && document.getValidUntil().isBefore(today)) {
            return Result.failure(VerificationError.DOCUMENT_ALREADY_EXPIRED);
        }
        if (type == PartnerDocumentType.VEHICLE_INSURANCE && requirements.commercialInsuranceRequiredForAnyVehicle()
                && insuranceUseOf(document) != InsuranceUseType.COMMERCIAL) {
            return Result.failure(VerificationError.INSURANCE_NOT_COMMERCIAL);
        }
        document.markVerified(operatorId, now);
        documents.save(document);
        audit.record(document.getAccountId(), operatorId, VerificationAudit.ACTOR_OPERATOR,
                VerificationAudit.DOCUMENT_VERIFIED, document.getId(), type, describe(document));
        return Result.success(toSummary(document));
    }

    /**
     * A renewal turned down while the document it was replacing is still
     * approved and in date: that one becomes current again, so being told
     * "this photo is blurred" does not also take her off the road.
     */
    private void restoreEarlierIfStillValid(PartnerDocumentEntity rejected, UUID operatorId, Instant now, LocalDate today) {
        UUID earlierId = rejected.getReplacesDocumentId();
        if (earlierId == null) {
            documents.save(rejected);
            return;
        }
        Optional<PartnerDocumentEntity> earlier = documents.findById(earlierId);
        if (earlier.isEmpty() || !inDate(earlier.get(), today)
                || earlier.get().getStatus() != PartnerDocumentStatus.VERIFIED) {
            documents.save(rejected);
            return;
        }
        rejected.supersede(now);
        documents.saveAndFlush(rejected);
        earlier.get().reinstate();
        documents.save(earlier.get());
        audit.record(rejected.getAccountId(), operatorId, VerificationAudit.ACTOR_OPERATOR,
                VerificationAudit.RENEWAL_REJECTED_EARLIER_KEPT, earlierId, rejected.getType(),
                "The earlier approved document stays in force until " + plain(earlier.get().getValidUntil()));
    }

    // --------------------------------------------------------------- readiness

    /**
     * Her documents' part of "may she work today": one blocker per required
     * document that is missing, waiting, turned down, expired or (for a
     * policy) not commercial - in the order of the checklist she sees.
     */
    @Transactional(readOnly = true)
    public DocumentsReadiness readiness(UUID accountId, String vehicleType) {
        LocalDate today = today();
        List<PartnerDocumentEntity> all = documents.findByAccountIdOrderByCreatedAtDesc(accountId);
        Map<PartnerDocumentType, PartnerDocumentEntity> current = new LinkedHashMap<>();
        Map<UUID, PartnerDocumentEntity> byId = new HashMap<>();
        for (PartnerDocumentEntity d : all) {
            byId.put(d.getId(), d);
            if (d.isCurrent()) {
                current.put(d.getType(), d);
            }
        }
        List<PartnerDocumentType> required = requirements.requiredFor(vehicleType);
        boolean commercial = requirements.commercialInsuranceRequired(vehicleType);
        List<Blocker> blockers = new ArrayList<>();
        List<Blocker> warnings = new ArrayList<>();
        List<DocumentState> states = new ArrayList<>();
        int reminderWindow = requirements.reminderDays().isEmpty() ? 0 : requirements.reminderDays().get(0);

        EnumSet<PartnerDocumentType> shown = EnumSet.noneOf(PartnerDocumentType.class);
        shown.addAll(required);
        shown.add(PartnerDocumentType.POLICE_CERTIFICATE);
        if (current.containsKey(PartnerDocumentType.BACKGROUND_CHECK_REPORT)) {
            shown.add(PartnerDocumentType.BACKGROUND_CHECK_REPORT);
        }
        for (PartnerDocumentType type : shown) {
            boolean isRequired = required.contains(type);
            PartnerDocumentEntity doc = current.get(type);
            PartnerDocumentEntity inForce = inForce(doc, byId, today);
            boolean renewalWaiting = doc != null && inForce != null && inForce != doc;
            PartnerDocumentEntity described = renewalWaiting ? inForce : doc;
            states.add(describe(type, isRequired, described, renewalWaiting));
            if (!isRequired) {
                continue;
            }
            Blocker problem = problemWith(type, doc, inForce, today, commercial);
            if (problem != null) {
                blockers.add(problem);
            } else if (inForce != null && inForce.getValidUntil() != null
                    && ChronoUnit.DAYS.between(today, inForce.getValidUntil()) <= reminderWindow && !renewalWaiting) {
                warnings.add(new Blocker(BlockerCode.DOCUMENT_EXPIRING, type, inForce.getValidUntil(),
                        "Your " + type.plainName() + " expires on " + plain(inForce.getValidUntil())
                                + ". Upload the new one before then to keep going online."));
            }
        }
        return new DocumentsReadiness(blockers, warnings, states, requirements.enforced());
    }

    /** Her documents' blockers and warnings, and whether the rule is enforced - see DocumentRequirements.enforced. */
    public record DocumentsReadiness(List<Blocker> blockers, List<Blocker> warnings, List<DocumentState> documents,
                                     boolean enforced) {
    }

    private Blocker problemWith(PartnerDocumentType type, PartnerDocumentEntity doc, PartnerDocumentEntity inForce,
                                LocalDate today, boolean commercial) {
        String name = type.plainName();
        if (inForce != null) {
            if (type == PartnerDocumentType.VEHICLE_INSURANCE && commercial
                    && insuranceUseOf(inForce) != InsuranceUseType.COMMERCIAL) {
                return new Blocker(BlockerCode.INSURANCE_NOT_COMMERCIAL, type, null,
                        "Your vehicle insurance is not for commercial use. A private policy can be refused when you"
                                + " carry paying passengers. Upload a commercial policy to go online.");
            }
            return null;
        }
        if (doc == null) {
            return new Blocker(BlockerCode.DOCUMENT_MISSING, type, null,
                    "Upload your " + name + " to go online.");
        }
        return switch (doc.getStatus()) {
            case PENDING, UNDER_REVIEW -> new Blocker(BlockerCode.DOCUMENT_UNDER_REVIEW, type, null,
                    "Your " + name + " is being checked. You can go online once it is approved.");
            case REJECTED -> new Blocker(BlockerCode.DOCUMENT_REJECTED, type, null,
                    "Your " + name + " was not accepted: " + doc.getRejectionReason() + " Upload it again to go online.");
            case EXPIRED, VERIFIED -> new Blocker(BlockerCode.DOCUMENT_EXPIRED, type, doc.getValidUntil(),
                    "Your " + name + " expired on " + plain(doc.getValidUntil()) + ". Upload the new one to go online.");
        };
    }

    /**
     * The row that counts for this type today, or null: the current row if
     * it is approved and in date, or the approved, in-date row a waiting
     * renewal is replacing.
     */
    private PartnerDocumentEntity inForce(PartnerDocumentEntity current, Map<UUID, PartnerDocumentEntity> byId,
                                          LocalDate today) {
        if (current == null) {
            return null;
        }
        if (current.getStatus() == PartnerDocumentStatus.VERIFIED && inDate(current, today)) {
            return current;
        }
        if ((current.getStatus() == PartnerDocumentStatus.UNDER_REVIEW || current.getStatus() == PartnerDocumentStatus.PENDING)
                && current.getReplacesDocumentId() != null) {
            PartnerDocumentEntity earlier = byId.get(current.getReplacesDocumentId());
            if (earlier != null && earlier.getStatus() == PartnerDocumentStatus.VERIFIED && inDate(earlier, today)) {
                return earlier;
            }
        }
        return null;
    }

    private static boolean stillCounts(PartnerDocumentEntity doc, LocalDate today) {
        return doc.getStatus() == PartnerDocumentStatus.VERIFIED && inDate(doc, today);
    }

    /** valid_until is the last good day: a licence valid until the 12th is good all of the 12th. */
    private static boolean inDate(PartnerDocumentEntity doc, LocalDate today) {
        return doc.getValidUntil() == null || !doc.getValidUntil().isBefore(today);
    }

    // ------------------------------------------------------------------- sweep

    /**
     * Expires every document in force whose date has passed, then sends the
     * 30/7/1-day reminders. Returns how many documents expired.
     * <p>
     * An expiry publishes VerificationLapsed, which takes her offline if
     * she is online - after the trip she is on, because going offline never
     * touches a live trip - and dispatch stops offering her the next one.
     */
    @Transactional
    public int expireAndRemind() {
        LocalDate today = today();
        Instant now = clock.instant();
        int expired = 0;
        for (PartnerDocumentEntity doc : documents.findVerifiedExpiredBefore(today)) {
            doc.markExpired(now);
            documents.save(doc);
            audit.record(doc.getAccountId(), null, VerificationAudit.ACTOR_SYSTEM, VerificationAudit.DOCUMENT_EXPIRED,
                    doc.getId(), doc.getType(), "Valid until " + plain(doc.getValidUntil()));
            events.publish(new VerificationLapsed(doc.getAccountId(), VerificationLapsed.Cause.DOCUMENT_EXPIRED,
                    doc.getType(), doc.getValidUntil()));
            expired++;
        }
        List<Integer> thresholds = requirements.reminderDays();
        if (!thresholds.isEmpty()) {
            for (PartnerDocumentEntity doc : documents.findVerifiedValidUntilOnOrBefore(today.plusDays(thresholds.get(0)))) {
                int daysLeft = (int) ChronoUnit.DAYS.between(today, doc.getValidUntil());
                Integer due = null;
                for (int threshold : thresholds) {
                    if (daysLeft <= threshold) {
                        due = threshold;
                    }
                }
                if (due == null || (doc.getLastReminderDays() != null && doc.getLastReminderDays() <= due)) {
                    continue;
                }
                doc.recordReminder(due);
                documents.save(doc);
                events.publish(new PartnerDocumentExpiring(doc.getAccountId(), doc.getType(), doc.getValidUntil(), daysLeft));
            }
        }
        if (expired > 0) {
            log.info("Expired {} partner document(s)", expired);
        }
        return expired;
    }

    // ---------------------------------------------------------- operator reads

    @Transactional(readOnly = true)
    public List<PartnerDocumentSummary> history(UUID accountId) {
        return documents.findByAccountIdOrderByCreatedAtDesc(accountId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerDocumentSummary> awaitingReview() {
        return documents.findBySupersededAtIsNullAndStatusInOrderBySubmittedAtAsc(
                List.of(PartnerDocumentStatus.UNDER_REVIEW, PartnerDocumentStatus.PENDING)).stream()
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerDocumentSummary> expiringWithin(int days) {
        return documents.findVerifiedValidUntilOnOrBefore(today().plusDays(days)).stream()
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerDocumentSummary> expired() {
        return documents.findBySupersededAtIsNullAndStatusOrderByExpiredAtDesc(PartnerDocumentStatus.EXPIRED).stream()
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PartnerDocumentSummary> find(UUID documentId) {
        return documents.findById(documentId).map(this::toSummary);
    }

    /**
     * A short-lived link to the file, for an operator - and a line in her
     * audit trail saying who opened it and when, written before the link is
     * handed out. Every document, not only police evidence: the cost is a
     * row, and "who has seen her licence" is a fair question too.
     */
    @Transactional
    public Optional<String> open(UUID documentId, UUID operatorId) {
        Optional<PartnerDocumentEntity> found = documents.findById(documentId);
        if (found.isEmpty() || found.get().getDocumentKey() == null) {
            return Optional.empty();
        }
        PartnerDocumentEntity doc = found.get();
        audit.record(doc.getAccountId(), operatorId, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.DOCUMENT_VIEWED,
                doc.getId(), doc.getType(), null);
        return Optional.ofNullable(storage.resolveUrl(doc.getDocumentKey()));
    }

    /**
     * A background check sent to a provider: a PENDING report row holding
     * the provider's reference, with nothing to read until it answers.
     */
    @Transactional
    public PartnerDocumentSummary openProviderCheck(UUID accountId, String providerReference, UUID operatorId) {
        Instant now = clock.instant();
        documents.findByAccountIdAndTypeAndSupersededAtIsNull(accountId, PartnerDocumentType.BACKGROUND_CHECK_REPORT)
                .ifPresent(earlier -> {
                    earlier.supersede(now);
                    documents.saveAndFlush(earlier);
                });
        PartnerDocumentEntity report = new PartnerDocumentEntity(accountId, PartnerDocumentType.BACKGROUND_CHECK_REPORT,
                PartnerDocumentSource.PROVIDER, PartnerDocumentStatus.PENDING, now);
        report.setProviderReference(providerReference);
        documents.save(report);
        audit.record(accountId, operatorId, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.BACKGROUND_CHECK_SUBMITTED,
                report.getId(), PartnerDocumentType.BACKGROUND_CHECK_REPORT, "Reference " + providerReference);
        return toSummary(report);
    }

    /**
     * A provider's answer: its report is filed on the waiting row, which
     * moves to UNDER_REVIEW for an operator to read. Nothing is decided here.
     * Empty when no check has that reference.
     */
    @Transactional
    public Optional<PartnerDocumentSummary> recordProviderResult(String providerReference, DocumentUpload report, String summary) {
        Optional<PartnerDocumentEntity> found = documents.findFirstByProviderReferenceOrderByCreatedAtDesc(providerReference);
        if (found.isEmpty() || !found.get().isCurrent()) {
            return Optional.empty();
        }
        PartnerDocumentEntity doc = found.get();
        if (report != null && DocumentRules.check(report) == null) {
            if (doc.getDocumentKey() != null) {
                storage.delete(doc.getDocumentKey());
            }
            doc.setDocumentKey(storage.store(doc.getAccountId(), "partner-background_check_report", DocumentRules.asDetected(report)));
        }
        if (doc.getStatus() == PartnerDocumentStatus.PENDING) {
            doc.markUnderReview();
        }
        documents.save(doc);
        audit.record(doc.getAccountId(), null, VerificationAudit.ACTOR_PROVIDER, VerificationAudit.PROVIDER_RESULT_RECEIVED,
                doc.getId(), doc.getType(), summary);
        return Optional.of(toSummary(doc));
    }

    /** For PoliceVerificationService, which attaches evidence by id. */
    Optional<PartnerDocumentEntity> entity(UUID documentId) {
        return documents.findById(documentId);
    }

    /**
     * The police check approved on this certificate: it is approved too,
     * with the number and issue date the operator just recorded, if it was
     * still waiting. One reading of one piece of paper, one decision.
     */
    void acceptAsPoliceEvidence(PartnerDocumentEntity evidence, UUID operatorId, String number, LocalDate issuedOn) {
        if (evidence.getDocumentNumber() == null) {
            evidence.setDocumentNumber(number);
        }
        if (evidence.getIssuedOn() == null) {
            evidence.setIssuedOn(issuedOn);
        }
        if (evidence.getStatus() == PartnerDocumentStatus.UNDER_REVIEW || evidence.getStatus() == PartnerDocumentStatus.PENDING) {
            evidence.markVerified(operatorId, clock.instant());
            audit.record(evidence.getAccountId(), operatorId, VerificationAudit.ACTOR_OPERATOR,
                    VerificationAudit.DOCUMENT_VERIFIED, evidence.getId(), evidence.getType(), describe(evidence));
        }
        documents.save(evidence);
    }

    Optional<PartnerDocumentEntity> current(UUID accountId, PartnerDocumentType type) {
        return documents.findByAccountIdAndTypeAndSupersededAtIsNull(accountId, type);
    }

    // ---------------------------------------------------------------- deletion

    /**
     * Account deletion: every file she sent, every version, deleted from
     * storage, and the personal facts on each row cleared. The rows stay with
     * their type, status and dates, because whether she was cleared to carry
     * passengers is part of the record of the trips she took.
     */
    @Transactional
    public void eraseFor(UUID accountId) {
        for (PartnerDocumentEntity doc : documents.findByAccountIdOrderByCreatedAtDesc(accountId)) {
            if (doc.getDocumentKey() != null) {
                storage.delete(doc.getDocumentKey());
            }
            doc.eraseForDeletion();
            documents.save(doc);
        }
        audit.eraseDetailsFor(accountId);
    }

    // ----------------------------------------------------------------- helpers

    private static VerificationError fileProblem(DocumentUpload upload) {
        DocumentRules.Problem problem = DocumentRules.check(upload);
        if (problem == null) {
            return null;
        }
        return switch (problem) {
            case UNSUPPORTED_TYPE -> VerificationError.DOCUMENT_TYPE_UNSUPPORTED;
            case TOO_SMALL -> VerificationError.DOCUMENT_TOO_SMALL;
            case TOO_LARGE -> VerificationError.DOCUMENT_TOO_LARGE;
        };
    }

    private static VerificationError dateProblem(DocumentFacts facts, LocalDate today) {
        if (facts.issuedOn() != null && facts.issuedOn().isAfter(today)) {
            return VerificationError.ISSUE_DATE_IN_FUTURE;
        }
        if (facts.validUntil() != null && facts.validUntil().isBefore(today)) {
            return VerificationError.DOCUMENT_ALREADY_EXPIRED;
        }
        if (facts.issuedOn() != null && facts.validUntil() != null && facts.validUntil().isBefore(facts.issuedOn())) {
            return VerificationError.DOCUMENT_ALREADY_EXPIRED;
        }
        return null;
    }

    private void applyFacts(PartnerDocumentEntity document, DocumentFacts facts) {
        if (!blank(facts.documentNumber())) {
            String number = facts.documentNumber().trim().toUpperCase(Locale.ROOT);
            document.setDocumentNumber(number.length() > 64 ? number.substring(0, 64) : number);
        }
        if (facts.issuedOn() != null) {
            document.setIssuedOn(facts.issuedOn());
        }
        if (facts.validUntil() != null) {
            document.setValidUntil(facts.validUntil());
        }
        if (facts.insuranceUseType() != null && document.getType() == PartnerDocumentType.VEHICLE_INSURANCE) {
            Map<String, Object> meta = metadata(document);
            meta.put(META_INSURANCE_USE, facts.insuranceUseType().name());
            document.setMetadataJson(write(meta));
        }
    }

    InsuranceUseType insuranceUseOf(PartnerDocumentEntity document) {
        Object value = metadata(document).get(META_INSURANCE_USE);
        if (value == null) {
            return InsuranceUseType.UNKNOWN;
        }
        try {
            return InsuranceUseType.valueOf(value.toString());
        } catch (IllegalArgumentException ex) {
            return InsuranceUseType.UNKNOWN;
        }
    }

    private Map<String, Object> metadata(PartnerDocumentEntity document) {
        if (blank(document.getMetadataJson())) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(json.readValue(document.getMetadataJson(), new TypeReference<Map<String, Object>>() { }));
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private String write(Map<String, Object> meta) {
        try {
            return json.writeValueAsString(meta);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private DocumentState describe(PartnerDocumentType type, boolean required, PartnerDocumentEntity doc, boolean renewalWaiting) {
        if (doc == null) {
            return new DocumentState(type, required, null, null, null, null, null, null, null, false);
        }
        return new DocumentState(type, required, doc.getStatus(), doc.getDocumentNumber(), doc.getIssuedOn(),
                doc.getValidUntil(), doc.getStatus() == PartnerDocumentStatus.REJECTED ? doc.getRejectionReason() : null,
                type == PartnerDocumentType.VEHICLE_INSURANCE ? insuranceUseOf(doc) : null,
                doc.getSubmittedAt(), renewalWaiting);
    }

    private String describe(PartnerDocumentEntity doc) {
        StringBuilder out = new StringBuilder();
        if (doc.getDocumentNumber() != null) {
            out.append("No. ").append(doc.getDocumentNumber());
        }
        if (doc.getValidUntil() != null) {
            out.append(out.isEmpty() ? "" : ", ").append("valid until ").append(plain(doc.getValidUntil()));
        }
        if (doc.getType() == PartnerDocumentType.VEHICLE_INSURANCE) {
            out.append(out.isEmpty() ? "" : ", ").append(insuranceUseOf(doc).name().toLowerCase(Locale.ROOT)).append(" use");
        }
        return out.isEmpty() ? null : out.toString();
    }

    PartnerDocumentSummary toSummary(PartnerDocumentEntity d) {
        return new PartnerDocumentSummary(d.getId(), d.getAccountId(), d.getType(), d.getStatus(), d.getDocumentNumber(),
                d.getIssuedOn(), d.getValidUntil(), d.getRejectionReason(),
                d.getType() == PartnerDocumentType.VEHICLE_INSURANCE ? insuranceUseOf(d) : null,
                d.getSource(), d.getProviderReference(), d.getDocumentKey() != null, d.getSubmittedAt(),
                d.getReviewedAt(), d.getReviewedBy(), d.getSupersededAt(), d.getReplacesDocumentId());
    }

    private static String actorRoleFor(PartnerDocumentSource source) {
        return switch (source) {
            case PARTNER_UPLOAD -> VerificationAudit.ACTOR_PARTNER;
            case OPERATOR_UPLOAD -> VerificationAudit.ACTOR_OPERATOR;
            case PROVIDER -> VerificationAudit.ACTOR_PROVIDER;
        };
    }

    static String plain(LocalDate date) {
        return date == null ? "an unknown date" : PLAIN_DATE.format(date);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
