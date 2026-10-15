package com.sheout.driververification.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.ApplicantProfiles;
import com.sheout.driververification.ApplicantProfiles.ApplicantProfile;
import com.sheout.driververification.CheckFinding;
import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;
import com.sheout.driververification.NameOnFile;
import com.sheout.driververification.RejectionReason;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.VerificationCheck;
import com.sheout.driververification.VerificationCheck.Finding;
import com.sheout.driververification.VerificationChecks;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.internal.checks.CheckProvider;
import com.sheout.driververification.internal.checks.CheckProviders;
import com.sheout.driververification.internal.checks.ManualCheckProvider;
import com.sheout.sharedkernel.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The checks engine: works out every check on an account from what she has
 * sent, what the automatic rules notice (CheckRules), what the check's
 * provider says (CheckProviders), and what a person decided.
 * <p>
 * WHAT DECIDES A CHECK, in this order:
 * <ol>
 *   <li>Nothing sent yet, or a person sent it back: PENDING.</li>
 *   <li>A person approved the thing it is about (her ID, the document, her
 *       police check), or two people overrode a failure: APPROVED_BY_PERSON.
 *       That is read from the existing decisions rather than kept apart
 *       from them, so the check and the decision cannot disagree.</li>
 *   <li>A finding that fails (an expired licence): FAILED_AUTO.</li>
 *   <li>The provider's answer, asked once per thing sent: PASSED only when
 *       it passed and the rules noticed nothing; otherwise NEEDS_REVIEW.</li>
 * </ol>
 * Nothing here rejects anybody. A person sends things back, through the
 * same review endpoints as before, and those refuse to approve over a
 * FAILED_AUTO check or past a finding the reviewer has not acknowledged.
 * <p>
 * refresh() is cheap and idempotent - it writes only what changed - so it is
 * run after every submission and decision, and when a reviewer opens her.
 * Opening her is what notices a duplicate that arrived on another account
 * after she was last looked at.
 */
@Service
public class VerificationCheckService {

    private static final Logger log = LoggerFactory.getLogger(VerificationCheckService.class);
    /** A plain mapper: the application's masks personal data during console requests. */
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Finding>> FINDINGS = new TypeReference<>() {
    };
    private static final List<CheckType> PARTNER_DOCUMENT_CHECKS = List.of(CheckType.LICENCE, CheckType.VEHICLE, CheckType.INSURANCE);

    private final VerificationCheckRepository checks;
    private final VerificationRecordRepository records;
    private final PartnerDocumentRepository documents;
    private final PoliceVerificationRepository policeDecisions;
    private final DocumentRequirements requirements;
    private final CheckProviders providers;
    private final CheckRules rules;
    private final VerificationAudit audit;
    private final ObjectProvider<ApplicantProfiles> profiles;
    private final FlaggedDocumentNumberRepository flagged;
    private final com.sheout.sharedkernel.event.DomainEventPublisher events;
    private final Clock clock;

    VerificationCheckService(VerificationCheckRepository checks, VerificationRecordRepository records,
                             PartnerDocumentRepository documents, PoliceVerificationRepository policeDecisions,
                             DocumentRequirements requirements, CheckProviders providers, CheckRules rules,
                             VerificationAudit audit, ObjectProvider<ApplicantProfiles> profiles,
                             FlaggedDocumentNumberRepository flagged,
                             com.sheout.sharedkernel.event.DomainEventPublisher events) {
        this.events = events;
        this.flagged = flagged;
        this.checks = checks;
        this.records = records;
        this.documents = documents;
        this.policeDecisions = policeDecisions;
        this.requirements = requirements;
        this.providers = providers;
        this.rules = rules;
        this.audit = audit;
        this.profiles = profiles;
        this.clock = Clock.systemUTC();
    }

    /** The document behind each partner document check. */
    static PartnerDocumentType documentFor(CheckType type) {
        return switch (type) {
            case LICENCE -> PartnerDocumentType.DRIVING_LICENCE;
            case VEHICLE -> PartnerDocumentType.VEHICLE_RC;
            case INSURANCE -> PartnerDocumentType.VEHICLE_INSURANCE;
            default -> null;
        };
    }

    /** The check a partner document is behind, or null for one no check covers (PUC, fitness). */
    static CheckType checkFor(PartnerDocumentType type) {
        return switch (type) {
            case DRIVING_LICENCE -> CheckType.LICENCE;
            case VEHICLE_RC -> CheckType.VEHICLE;
            case VEHICLE_INSURANCE -> CheckType.INSURANCE;
            case POLICE_CERTIFICATE, BACKGROUND_CHECK_REPORT -> CheckType.BACKGROUND;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ reads

    /** Works every check out again, writes what changed, and answers with all of them. */
    @Transactional
    public VerificationChecks refresh(UUID accountId) {
        Optional<VerificationRecordEntity> found = records.findByAccountId(accountId);
        if (found.isEmpty()) {
            return new VerificationChecks(accountId, null, List.of(), VerificationChecks.Overall.WAITING_FOR_APPLICANT);
        }
        VerificationRecordEntity record = found.get();
        ApplicantProfile profile = profile(accountId);
        Map<CheckType, VerificationCheckEntity> existing = new EnumMap<>(CheckType.class);
        checks.findByAccountId(accountId).forEach(c -> existing.put(c.getType(), c));
        Map<CheckType, Snapshot> before = new EnumMap<>(CheckType.class);
        existing.forEach((type, row) -> before.put(type, Snapshot.of(row)));
        Instant now = clock.instant();
        List<VerificationCheckEntity> current = new ArrayList<>();
        for (Plan plan : plan(record, profile)) {
            VerificationCheckEntity row = existing.get(plan.type());
            if (row == null) {
                row = new VerificationCheckEntity(accountId, plan.type(), now);
            }
            if (apply(row, plan, profile, record, now)) {
                checks.save(row);
            }
            current.add(row);
        }
        if (!before.isEmpty()) {
            Map<CheckType, Snapshot> after = new EnumMap<>(CheckType.class);
            current.forEach(row -> after.put(row.getType(), Snapshot.of(row)));
            stages(record, before, after).forEach(stage ->
                    events.publish(new com.sheout.driververification.VerificationStageReached(accountId, record.getRole(), stage)));
        }
        return view(accountId, record.getRole(), current);
    }

    /** What a check looked like, for telling a step forward from a step that was already taken. */
    record Snapshot(boolean required, CheckStatus status, String subjectVersion) {
        static Snapshot of(VerificationCheckEntity row) {
            return new Snapshot(row.isRequired(), row.getStatus(), row.getSubjectVersion());
        }
    }

    /**
     * The stages she has just reached, between the checks as they were and as
     * they are. Only ever called for an account whose checks already existed,
     * so the first look at an old account never announces anything.
     */
    static List<com.sheout.driververification.VerificationStageReached.Stage> stages(
            VerificationRecordEntity record, Map<CheckType, Snapshot> before, Map<CheckType, Snapshot> after) {
        List<com.sheout.driververification.VerificationStageReached.Stage> out = new ArrayList<>();
        Snapshot idBefore = before.get(CheckType.IDENTITY);
        Snapshot idAfter = after.get(CheckType.IDENTITY);
        if (idAfter != null && idAfter.subjectVersion() != null && idAfter.status() != CheckStatus.APPROVED_BY_PERSON
                && (idBefore == null || !Objects.equals(idBefore.subjectVersion(), idAfter.subjectVersion()))) {
            out.add(com.sheout.driververification.VerificationStageReached.Stage.ID_RECEIVED);
        }
        if (record.getRole() != AccountRole.DRIVER) {
            return out;
        }
        if (idAfter != null && idAfter.status() == CheckStatus.APPROVED_BY_PERSON
                && (idBefore == null || idBefore.status() != CheckStatus.APPROVED_BY_PERSON)) {
            out.add(com.sheout.driververification.VerificationStageReached.Stage.IDENTITY_APPROVED);
        }
        if (!allSent(before) && allSent(after)) {
            out.add(com.sheout.driververification.VerificationStageReached.Stage.VEHICLE_PAPERS_RECEIVED);
        }
        if (!allApproved(before) && allApproved(after)) {
            out.add(com.sheout.driververification.VerificationStageReached.Stage.VEHICLE_APPROVED);
        }
        Snapshot policeBefore = before.get(CheckType.BACKGROUND);
        Snapshot policeAfter = after.get(CheckType.BACKGROUND);
        if (policeAfter != null && policeAfter.subjectVersion() != null && policeAfter.subjectVersion().startsWith("doc:")
                && policeAfter.status() != CheckStatus.APPROVED_BY_PERSON
                && (policeBefore == null || !Objects.equals(policeBefore.subjectVersion(), policeAfter.subjectVersion()))) {
            out.add(com.sheout.driververification.VerificationStageReached.Stage.SAFETY_CHECK_RECEIVED);
        }
        return out;
    }

    private static boolean allSent(Map<CheckType, Snapshot> checks) {
        List<Snapshot> papers = PARTNER_DOCUMENT_CHECKS.stream().map(checks::get).filter(Objects::nonNull)
                .filter(Snapshot::required).toList();
        return !papers.isEmpty() && papers.stream().allMatch(s -> s.subjectVersion() != null && s.status() != CheckStatus.PENDING);
    }

    private static boolean allApproved(Map<CheckType, Snapshot> checks) {
        List<Snapshot> papers = PARTNER_DOCUMENT_CHECKS.stream().map(checks::get).filter(Objects::nonNull)
                .filter(Snapshot::required).toList();
        return !papers.isEmpty() && papers.stream().allMatch(s -> s.status().satisfied());
    }

    // ------------------------------------------------------------- decisions

    /**
     * Whether a person may approve the thing these checks are about, now.
     * Refused when one of them failed automatically (and was not overridden
     * by two people), or when the automatic checks noticed something the
     * reviewer has not acknowledged - ticking it is the reviewer saying she
     * looked.
     */
    @Transactional
    public Result<Void, VerificationError> mayApprove(UUID accountId, Collection<CheckType> types,
                                                      Set<CheckFinding> acknowledged) {
        VerificationChecks all = refresh(accountId);
        Set<CheckFinding> ticked = acknowledged == null ? Set.of() : acknowledged;
        for (CheckType type : types) {
            Optional<VerificationCheck> check = all.find(type);
            if (check.isEmpty()) {
                continue;
            }
            if (check.get().status() == CheckStatus.FAILED_AUTO) {
                return Result.failure(VerificationError.CHECK_FAILED);
            }
            if (check.get().status() == CheckStatus.NEEDS_REVIEW
                    && !check.get().findings().stream().allMatch(f -> ticked.contains(f.code()))) {
                return Result.failure(VerificationError.FINDINGS_NOT_ACKNOWLEDGED);
            }
        }
        return Result.success(null);
    }

    // ------------------------------------------------------- remembered numbers

    /**
     * A number a person turned down for fraud or a confirmed mismatch, kept
     * so it fails its check if it comes back on any account. Only for a
     * reason that says so (RejectionReason.remembersNumber): never an
     * expired document, a blurry photo or a typing mistake.
     */
    @Transactional
    public void rememberNumber(PartnerDocumentEntity document, RejectionReason reason, UUID operatorId) {
        if (reason == null || !reason.remembersNumber() || document.getDocumentNumber() == null
                || flagged.existsByDocumentId(document.getId())) {
            return;
        }
        flagged.save(new FlaggedDocumentNumberEntity(document.getType(), document.getDocumentNumber(), reason.name(),
                document.getAccountId(), document.getId(), operatorId));
    }

    // ---------------------------------------------------------------- overrides

    /**
     * What an override carries from the person who asks to the person who
     * signs off. subjectVersion pins it to what she had sent when it was
     * asked: if she sends something new meanwhile, the override is refused.
     */
    public record OverrideRequest(UUID accountId, CheckType type, String subjectVersion, String note, UUID requestedBy) {
    }

    /**
     * The first person's half: a failed check that may be overridden, and a
     * written note (at least a sentence) on why. Recorded on her audit trail;
     * nothing changes until a second person signs off (applyOverride).
     */
    @Transactional
    public Result<OverrideRequest, VerificationError> prepareOverride(UUID accountId, CheckType type, String note, UUID requestedBy) {
        String trimmed = note == null ? "" : note.trim().replaceAll("\\s+", " ");
        if (trimmed.length() < 10) {
            return Result.failure(VerificationError.OVERRIDE_NOTE_REQUIRED);
        }
        if (trimmed.length() > 1000) {
            trimmed = trimmed.substring(0, 1000);
        }
        Optional<VerificationCheck> check = refresh(accountId).find(type);
        if (check.isEmpty() || !check.get().overridable()) {
            return Result.failure(VerificationError.NOT_OVERRIDABLE);
        }
        VerificationCheckEntity row = checks.findByAccountIdAndType(accountId, type).orElseThrow();
        audit.record(accountId, requestedBy, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.CHECK_OVERRIDE_REQUESTED,
                row.getSubjectId(), null, type + ": " + trimmed);
        return Result.success(new OverrideRequest(accountId, type, row.getSubjectVersion(), trimmed, requestedBy));
    }

    /**
     * The second person's half, run when they approve it (staff Approvals).
     * Refused if it is the same person, if she has sent something new, or if
     * the check no longer fails in a way that may be overridden.
     */
    @Transactional
    public Result<VerificationCheck, VerificationError> applyOverride(OverrideRequest request, UUID approvedBy) {
        if (approvedBy == null || approvedBy.equals(request.requestedBy())) {
            return Result.failure(VerificationError.NEEDS_SECOND_PERSON);
        }
        refresh(request.accountId());
        Optional<VerificationCheckEntity> found = checks.findByAccountIdAndType(request.accountId(), request.type());
        if (found.isEmpty() || !Objects.equals(found.get().getSubjectVersion(), request.subjectVersion())) {
            return Result.failure(VerificationError.CHECK_CHANGED);
        }
        VerificationCheckEntity row = found.get();
        if (!toView(row).overridable()) {
            return Result.failure(VerificationError.NOT_OVERRIDABLE);
        }
        row.recordOverride(request.note(), request.requestedBy(), approvedBy);
        row.decide(CheckStatus.APPROVED_BY_PERSON, "Accepted over a failed check by two people.", approvedBy,
                VerificationAudit.ACTOR_OPERATOR, clock.instant());
        checks.save(row);
        audit.record(request.accountId(), approvedBy, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.CHECK_OVERRIDDEN,
                row.getSubjectId(), null, request.type() + ": asked for by " + request.requestedBy() + ". " + request.note());
        return Result.success(toView(row));
    }

    // ---------------------------------------------------------------- names

    /**
     * Her name everywhere it is written, against her profile, for the
     * reviewer: the profile, the name she typed for her licence (or that she
     * declared it the same), and the RC's owner. The ID's name is not typed
     * anywhere, so it says to read it off the photo.
     */
    @Transactional(readOnly = true)
    public List<NameOnFile> namesOnFile(UUID accountId) {
        ApplicantProfile profile = profile(accountId);
        String profileName = profile == null ? null : profile.name();
        List<NameOnFile> out = new ArrayList<>();
        out.add(new NameOnFile("Profile", profileName, "PROFILE"));
        out.add(new NameOnFile("ID document", null, "READ_FROM_PHOTO"));
        documents.findByAccountIdAndTypeAndSupersededAtIsNull(accountId, PartnerDocumentType.DRIVING_LICENCE).ifPresent(licence -> {
            Map<String, Object> meta = CheckRules.metadata(licence);
            if (Boolean.TRUE.equals(meta.get(CheckRules.META_NAME_DECLARED))) {
                out.add(new NameOnFile("Licence", profileName, "DECLARED"));
            } else if (meta.get(CheckRules.META_NAME_ON_DOCUMENT) != null) {
                String typed = meta.get(CheckRules.META_NAME_ON_DOCUMENT).toString();
                out.add(new NameOnFile("Licence", typed, profileName == null ? "DIFFERENT"
                        : NameMatcher.compare(typed, profileName).name()));
            } else {
                out.add(new NameOnFile("Licence", null, "READ_FROM_PHOTO"));
            }
        });
        documents.findByAccountIdAndTypeAndSupersededAtIsNull(accountId, PartnerDocumentType.VEHICLE_RC).ifPresent(rc -> {
            Map<String, Object> meta = CheckRules.metadata(rc);
            Object owner = meta.get("vehicleOwner");
            if ("SELF".equals(owner)) {
                out.add(new NameOnFile("RC owner", profileName, "DECLARED"));
            } else if (owner != null) {
                Object name = meta.get("ownerName");
                out.add(new NameOnFile("RC owner (" + owner.toString().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + ")",
                        name == null ? null : name.toString(), "FAMILY"));
            } else {
                out.add(new NameOnFile("RC owner", null, "READ_FROM_PHOTO"));
            }
        });
        return out;
    }

    /** Account deletion: see VerificationCheckEntity.eraseForDeletion. */
    @Transactional
    public void eraseFor(UUID accountId) {
        for (VerificationCheckEntity row : checks.findByAccountId(accountId)) {
            row.eraseForDeletion();
            checks.save(row);
        }
    }

    // ---------------------------------------------------------------- working

    /**
     * What one check is about and what a person said about it. subjectVersion
     * changes whenever she sends it again; person is null when nobody has
     * decided this version.
     */
    record Plan(CheckType type, boolean required, UUID subjectId, String subjectVersion,
                PartnerDocumentEntity document, Person person) {
    }

    record Person(boolean approved, UUID by, Instant at, String reason) {
    }

    private List<Plan> plan(VerificationRecordEntity record, ApplicantProfile profile) {
        List<Plan> plans = new ArrayList<>();
        boolean partner = record.getRole() == AccountRole.DRIVER;

        // ---- her ID and selfie, sent together and decided together
        String idVersion = record.getAadhaarDocumentKey() == null && record.getGenderVerificationStatus() != VerificationStatus.VERIFIED
                ? null
                : "id:" + (record.getDocumentSubmittedAt() == null ? "before-checks" : record.getDocumentSubmittedAt().toEpochMilli());
        Person idPerson = switch (record.getGenderVerificationStatus()) {
            case VERIFIED -> new Person(true, parse(record.getReviewedBy()), record.getReviewedAt(), null);
            case REJECTED -> new Person(false, parse(record.getReviewedBy()), record.getReviewedAt(), record.getRejectionReason());
            default -> null;
        };
        plans.add(new Plan(CheckType.IDENTITY, true, null, idVersion, null, idPerson));
        plans.add(new Plan(CheckType.SELFIE_MATCH, true, null, idVersion, null, idPerson));
        if (!partner) {
            return plans;
        }

        // ---- the documents her vehicle needs
        List<PartnerDocumentType> needed = requirements.requiredFor(profile == null ? null : profile.vehicleType());
        for (CheckType type : PARTNER_DOCUMENT_CHECKS) {
            PartnerDocumentType docType = documentFor(type);
            PartnerDocumentEntity doc = documents.findByAccountIdAndTypeAndSupersededAtIsNull(record.getAccountId(), docType)
                    .orElse(null);
            plans.add(new Plan(type, needed.contains(docType), doc == null ? null : doc.getId(),
                    doc == null ? null : "doc:" + doc.getId(), doc, personOn(doc)));
        }

        // ---- PAN: optional, for payouts, never part of approval
        plans.add(new Plan(CheckType.PAN, false, null, profile != null && profile.panGiven() ? "pan" : null, null, null));

        // ---- her police check and its evidence
        PartnerDocumentEntity evidence = documents.findByAccountIdAndTypeAndSupersededAtIsNull(record.getAccountId(),
                        PartnerDocumentType.POLICE_CERTIFICATE)
                .or(() -> documents.findByAccountIdAndTypeAndSupersededAtIsNull(record.getAccountId(),
                        PartnerDocumentType.BACKGROUND_CHECK_REPORT))
                .orElse(null);
        VerificationStatus police = record.getPoliceVerificationStatus();
        Person policePerson = null;
        if (police == VerificationStatus.VERIFIED || police == VerificationStatus.REJECTED) {
            Optional<PoliceVerificationEntity> decision = policeDecisions.findByAccountIdOrderByDecidedAtDesc(record.getAccountId())
                    .stream().findFirst();
            policePerson = new Person(police == VerificationStatus.VERIFIED,
                    decision.map(PoliceVerificationEntity::getDecidedBy).orElse(null),
                    decision.map(PoliceVerificationEntity::getDecidedAt).orElse(record.getUpdatedAt()),
                    police == VerificationStatus.REJECTED ? "Her police check was not accepted." : null);
        } else if (evidence != null && evidence.getStatus() == PartnerDocumentStatus.REJECTED) {
            policePerson = personOn(evidence);
        }
        String policeVersion = evidence != null ? "doc:" + evidence.getId()
                : policePerson != null ? "police:" + police.name() : null;
        plans.add(new Plan(CheckType.BACKGROUND, true, evidence == null ? null : evidence.getId(), policeVersion, evidence,
                policePerson));
        return plans;
    }

    private static Person personOn(PartnerDocumentEntity doc) {
        if (doc == null) {
            return null;
        }
        return switch (doc.getStatus()) {
            case VERIFIED -> new Person(true, doc.getReviewedBy(), doc.getReviewedAt(), null);
            case REJECTED -> new Person(false, doc.getReviewedBy(), doc.getReviewedAt(), doc.getRejectionReason());
            // EXPIRED: what a person approved has run out, which the rules see.
            default -> null;
        };
    }

    /** Brings one row in line with its plan. True when anything changed. */
    private boolean apply(VerificationCheckEntity row, Plan plan, ApplicantProfile profile, VerificationRecordEntity record,
                          Instant now) {
        boolean changed = row.getId() == null;
        if (row.isRequired() != plan.required()) {
            row.setRequired(plan.required());
            changed = true;
        }
        if (!Objects.equals(row.getSubjectVersion(), plan.subjectVersion())) {
            row.newSubject(plan.subjectId(), plan.subjectVersion());
            changed = true;
        }

        List<Finding> findings = plan.subjectVersion() == null ? List.of()
                : rules.findings(new CheckRules.Subject(row.getAccountId(), plan.type(), plan.document(), profile, record));
        String findingsJson = findings.isEmpty() ? null : write(findings);
        if (!Objects.equals(row.getFindingsJson(), findingsJson)) {
            row.setFindingsJson(findingsJson);
            changed = true;
        }

        Person person = plan.person();
        CheckStatus status;
        String reason;
        UUID by = null;
        String byRole = VerificationAudit.ACTOR_SYSTEM;
        Instant at = now;
        if (plan.subjectVersion() == null) {
            status = CheckStatus.PENDING;
            reason = waitingFor(plan.type());
        } else if (person != null && !person.approved()) {
            status = CheckStatus.PENDING;
            reason = "Sent back to her" + (person.reason() == null ? "." : ": " + person.reason());
            by = person.by();
            byRole = VerificationAudit.ACTOR_OPERATOR;
            at = person.at() == null ? now : person.at();
        } else if ((person != null && person.approved()) || row.getOverrideApprovedBy() != null) {
            status = CheckStatus.APPROVED_BY_PERSON;
            if (row.getStatus() == CheckStatus.APPROVED_BY_PERSON) {
                // Already recorded, by whoever did it first: keep their name and time.
                return changed;
            }
            reason = row.getOverrideApprovedBy() != null && (person == null || !person.approved())
                    ? "Accepted over a failed check by two people." : "Approved by a person.";
            if (person != null && person.approved()) {
                by = person.by();
                at = person.at() == null ? now : person.at();
            } else {
                by = row.getOverrideApprovedBy();
            }
            byRole = VerificationAudit.ACTOR_OPERATOR;
        } else {
            Optional<Finding> failing = findings.stream().filter(f -> f.code().fails()).findFirst();
            CheckProvider provider = providers.forType(plan.type());
            if (row.getProviderStatus() == null) {
                row.recordVerdict(provider.name(), ask(provider, row, plan));
                changed = true;
            }
            boolean fromProvider = !ManualCheckProvider.NAME.equals(row.getProvider());
            if (failing.isPresent()) {
                status = CheckStatus.FAILED_AUTO;
                reason = failing.get().code().text();
            } else if (row.getProviderStatus() == CheckStatus.FAILED_AUTO) {
                status = CheckStatus.FAILED_AUTO;
                reason = row.getProviderReason();
                byRole = fromProvider ? VerificationAudit.ACTOR_PROVIDER : byRole;
            } else if (row.getProviderStatus() == CheckStatus.PASSED && findings.isEmpty()) {
                status = CheckStatus.PASSED;
                reason = row.getProviderReason();
                byRole = fromProvider ? VerificationAudit.ACTOR_PROVIDER : byRole;
            } else {
                status = CheckStatus.NEEDS_REVIEW;
                reason = findings.isEmpty() ? row.getProviderReason() : findings.get(0).code().text();
                byRole = findings.isEmpty() && fromProvider ? VerificationAudit.ACTOR_PROVIDER : byRole;
            }
        }

        if (row.getStatus() == status && Objects.equals(row.getReason(), reason) && row.getId() != null) {
            return changed;
        }
        boolean software = !VerificationAudit.ACTOR_OPERATOR.equals(byRole);
        row.decide(status, reason, by, byRole, at);
        if (software) {
            // A person's decision is already on the trail as the decision itself.
            audit.record(row.getAccountId(), null, byRole, VerificationAudit.CHECK_UPDATED, plan.subjectId(),
                    plan.document() == null ? null : plan.document().getType(),
                    plan.type() + ": " + status + (reason == null ? "" : ". " + reason));
        }
        return true;
    }

    /** A provider that throws has not answered: a person looks instead, and the check waits for nobody. */
    private static CheckProvider.Verdict ask(CheckProvider provider, VerificationCheckEntity row, Plan plan) {
        try {
            return provider.check(new CheckProvider.Request(row.getAccountId(), plan.type(), plan.subjectId(),
                    plan.subjectVersion()));
        } catch (RuntimeException ex) {
            log.warn("Check provider {} failed for {} on {}: {}", provider.name(), plan.type(), row.getAccountId(), ex.toString());
            return new CheckProvider.Verdict(CheckStatus.NEEDS_REVIEW,
                    "The automatic check could not be reached. A person checks this instead.", null);
        }
    }

    private static String waitingFor(CheckType type) {
        return switch (type) {
            case IDENTITY, SELFIE_MATCH -> "Waiting for her ID and live selfie.";
            case LICENCE -> "Waiting for her driving licence.";
            case VEHICLE -> "Waiting for the vehicle's RC.";
            case INSURANCE -> "Waiting for the vehicle's insurance.";
            case PAN -> "Optional. Only used for payouts.";
            case BACKGROUND -> "Waiting for her police certificate.";
            case COURT_RECORD_SCREEN -> "Only when a reviewer screens public court records.";
        };
    }

    // ------------------------------------------------------------------ views

    VerificationChecks view(UUID accountId, AccountRole role, List<VerificationCheckEntity> rows) {
        List<VerificationCheck> out = rows.stream()
                .sorted(Comparator.comparing(VerificationCheckEntity::getType))
                .map(this::toView)
                .toList();
        return new VerificationChecks(accountId, role, out, VerificationChecks.overallOf(out));
    }

    VerificationCheck toView(VerificationCheckEntity row) {
        return new VerificationCheck(row.getType(), row.isRequired(), row.getStatus(), row.getReason(),
                read(row.getFindingsJson()), row.getProvider(), row.getSubjectId(), row.getDecidedBy(),
                row.getDecidedByRole(), row.getDecidedAt(), row.getOverrideNote(), row.getOverrideRequestedBy(),
                row.getOverrideApprovedBy());
    }

    private ApplicantProfile profile(UUID accountId) {
        ApplicantProfiles source = profiles.getIfAvailable();
        return source == null ? null : source.find(accountId).orElse(null);
    }

    private static UUID parse(String id) {
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String write(List<Finding> findings) {
        try {
            return JSON.writeValueAsString(findings);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static List<Finding> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(JSON.readValue(json, FINDINGS));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Unreadable findings on a check: {}", ex.getOriginalMessage());
            return List.of();
        }
    }
}
