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
    private final Clock clock;

    VerificationCheckService(VerificationCheckRepository checks, VerificationRecordRepository records,
                             PartnerDocumentRepository documents, PoliceVerificationRepository policeDecisions,
                             DocumentRequirements requirements, CheckProviders providers, CheckRules rules,
                             VerificationAudit audit, ObjectProvider<ApplicantProfiles> profiles) {
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
        Instant now = clock.instant();
        List<VerificationCheckEntity> current = new ArrayList<>();
        for (Plan plan : plan(record, profile)) {
            VerificationCheckEntity row = existing.get(plan.type());
            if (row == null) {
                row = new VerificationCheckEntity(accountId, plan.type(), now);
            }
            if (apply(row, plan, profile, now)) {
                checks.save(row);
            }
            current.add(row);
        }
        return view(accountId, record.getRole(), current);
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
    private boolean apply(VerificationCheckEntity row, Plan plan, ApplicantProfile profile, Instant now) {
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
                : rules.findings(new CheckRules.Subject(row.getAccountId(), plan.type(), plan.document(), profile));
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
