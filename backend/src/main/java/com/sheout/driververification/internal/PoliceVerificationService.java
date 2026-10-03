package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.PartnerDocumentExpiring;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.PoliceVerificationMethod;
import com.sheout.driververification.PoliceVerificationRecord;
import com.sheout.driververification.VerificationLapsed;
import com.sheout.driververification.VerificationStatus;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A partner's police check: recorded with how it was done and the evidence
 * it rests on, and redone on a period.
 * <p>
 * It used to be a button. An operator set VERIFIED or REJECTED and nothing
 * recorded what that rested on - no method, no certificate number, no date,
 * no document - so "was she police-verified" had an answer and "on what"
 * did not. Approving now needs all four, and the evidence must be hers,
 * on file, and a police certificate (or, only where configuration allows,
 * a background report). Each decision is a new row in police_verifications.
 * <p>
 * The check is still a person's decision. A provider can feed it evidence
 * later (VerificationProvider) without changing any of this.
 */
@Service
public class PoliceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PoliceVerificationService.class);

    /** What an operator records - see review. The documents are partner_documents ids. */
    public record PoliceDecision(
            VerificationStatus decision,
            PoliceVerificationMethod method,
            String certificateNumber,
            String issuingAuthority,
            LocalDate issuedOn,
            /** Null to take issuedOn + POLICE_REVERIFY_MONTHS. */
            LocalDate reverifyDueOn,
            UUID evidenceDocumentId,
            UUID extraEvidenceDocumentId,
            String reason) {

        /** The old console's shape: a bare decision, which approving now refuses. */
        public static PoliceDecision bare(VerificationStatus decision) {
            return new PoliceDecision(decision, null, null, null, null, null, null, null, null);
        }
    }

    private final VerificationRecordRepository records;
    private final PoliceVerificationRepository checks;
    private final PartnerDocumentService documents;
    private final VerificationConsent consent;
    private final PoliceVerificationRules rules;
    private final VerificationAudit audit;
    private final DomainEventPublisher events;
    private final Clock clock;

    @Autowired
    public PoliceVerificationService(VerificationRecordRepository records, PoliceVerificationRepository checks,
                                     PartnerDocumentService documents, VerificationConsent consent,
                                     PoliceVerificationRules rules, VerificationAudit audit, DomainEventPublisher events) {
        this(records, checks, documents, consent, rules, audit, events, Clock.systemUTC());
    }

    PoliceVerificationService(VerificationRecordRepository records, PoliceVerificationRepository checks,
                              PartnerDocumentService documents, VerificationConsent consent,
                              PoliceVerificationRules rules, VerificationAudit audit, DomainEventPublisher events,
                              Clock clock) {
        this.records = records;
        this.checks = checks;
        this.documents = documents;
        this.consent = consent;
        this.rules = rules;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock.withZone(PartnerDocumentService.INDIA));
    }

    int reverifyMonths() {
        return rules.reverifyMonths();
    }

    /** The last day the earliest re-verification reminder covers, from today. */
    LocalDate reminderWindowEnd(LocalDate today) {
        return rules.reminderDays().isEmpty() ? today : today.plusDays(rules.reminderDays().get(0));
    }

    /**
     * Records an operator's police-check decision.
     * <p>
     * In order: a real decision, a partner, her ID check passed (the police
     * check is on the woman the ID check confirmed), then for VERIFIED her
     * current consent and the evidence - method, certificate number, issue
     * date and the document - and for REJECTED a reason she can be told.
     * <p>
     * Approving also approves the certificate document itself if it was
     * still waiting: the operator has read it to fill in its number and date,
     * and asking for a second click on the same paper would be ceremony.
     */
    @Transactional
    public Result<VerificationRecordEntity, VerificationError> review(UUID accountId, UUID operatorId, PoliceDecision d) {
        if (d == null || (d.decision() != VerificationStatus.VERIFIED && d.decision() != VerificationStatus.REJECTED)) {
            return Result.failure(VerificationError.INVALID_DECISION);
        }
        Optional<VerificationRecordEntity> found = records.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(VerificationError.RECORD_NOT_FOUND);
        }
        VerificationRecordEntity record = found.get();
        if (record.getRole() != AccountRole.DRIVER) {
            return Result.failure(VerificationError.POLICE_VERIFICATION_NOT_APPLICABLE);
        }
        // The police check is on the woman the ID check has confirmed. Before
        // that - no ID yet, one under review, or one rejected - there is
        // nobody established to check.
        if (record.getGenderVerificationStatus() != VerificationStatus.VERIFIED) {
            return Result.failure(VerificationError.ID_CHECK_NOT_PASSED);
        }
        Instant now = clock.instant();
        if (d.decision() == VerificationStatus.REJECTED) {
            if (d.reason() == null || d.reason().isBlank()) {
                return Result.failure(VerificationError.REASON_REQUIRED);
            }
            checks.save(PoliceVerificationEntity.rejected(accountId, d.reason().trim(), d.evidenceDocumentId(),
                    record.getConsentAcceptedAt(), record.getConsentVersion(), operatorId, now));
            record.setPoliceVerificationStatus(VerificationStatus.REJECTED);
            record.setPoliceReverifyDueOn(null);
            records.save(record);
            audit.record(accountId, operatorId, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.POLICE_REJECTED,
                    d.evidenceDocumentId(), null, d.reason().trim());
            return Result.success(record);
        }

        if (!consent.isCurrent(record)) {
            return Result.failure(VerificationError.CONSENT_REQUIRED);
        }
        if (d.method() == null || blank(d.certificateNumber()) || d.issuedOn() == null || d.evidenceDocumentId() == null) {
            return Result.failure(VerificationError.POLICE_EVIDENCE_MISSING);
        }
        LocalDate today = today();
        if (d.issuedOn().isAfter(today)) {
            return Result.failure(VerificationError.ISSUE_DATE_IN_FUTURE);
        }
        PartnerDocumentEntity evidence = documents.entity(d.evidenceDocumentId()).orElse(null);
        if (!usableEvidence(evidence, accountId)) {
            return Result.failure(VerificationError.POLICE_EVIDENCE_INVALID);
        }
        // A background report is evidence of a different kind. Beside a
        // police certificate it is welcome; in place of one, only if a lawyer
        // has said so and configuration says so.
        boolean bgvAlone = d.method() == PoliceVerificationMethod.THIRD_PARTY_BGV
                || evidence.getType() == PartnerDocumentType.BACKGROUND_CHECK_REPORT;
        if (bgvAlone && !rules.acceptThirdPartyBgvAlone()) {
            return Result.failure(VerificationError.BGV_NOT_SUFFICIENT_ALONE);
        }
        if (d.extraEvidenceDocumentId() != null) {
            PartnerDocumentEntity extra = documents.entity(d.extraEvidenceDocumentId()).orElse(null);
            if (!usableEvidence(extra, accountId)) {
                return Result.failure(VerificationError.POLICE_EVIDENCE_INVALID);
            }
        }
        LocalDate due = d.reverifyDueOn() != null ? d.reverifyDueOn() : d.issuedOn().plusMonths(rules.reverifyMonths());
        if (!due.isAfter(today)) {
            return Result.failure(VerificationError.POLICE_CERTIFICATE_TOO_OLD);
        }

        String number = d.certificateNumber().trim();
        documents.acceptAsPoliceEvidence(evidence, operatorId, number, d.issuedOn());
        checks.save(PoliceVerificationEntity.verified(accountId, d.method(), number, d.issuedOn(),
                blank(d.issuingAuthority()) ? null : d.issuingAuthority().trim(), due, d.evidenceDocumentId(),
                d.extraEvidenceDocumentId(), record.getConsentAcceptedAt(), record.getConsentVersion(), operatorId, now));
        record.setPoliceVerificationStatus(VerificationStatus.VERIFIED);
        record.setPoliceReverifyDueOn(due);
        records.save(record);
        audit.record(accountId, operatorId, VerificationAudit.ACTOR_OPERATOR, VerificationAudit.POLICE_VERIFIED,
                d.evidenceDocumentId(), evidence.getType(), d.method() + ", No. " + number + ", issued "
                        + PartnerDocumentService.plain(d.issuedOn()) + ", re-verify by " + PartnerDocumentService.plain(due));
        return Result.success(record);
    }

    private static boolean usableEvidence(PartnerDocumentEntity doc, UUID accountId) {
        return doc != null && doc.getAccountId().equals(accountId) && doc.getType().policeEvidence()
                && doc.getDocumentKey() != null && doc.isCurrent()
                && doc.getStatus() != PartnerDocumentStatus.REJECTED && doc.getStatus() != PartnerDocumentStatus.EXPIRED;
    }

    /**
     * The police check's calendar: reminders 30 and 7 days before it falls
     * due, and on the day, back to PENDING - she is taken offline after any
     * trip she is on, and told to send a new certificate. Returns how many
     * fell due.
     */
    @Transactional
    public int reverifyDue() {
        LocalDate today = today();
        int due = 0;
        for (VerificationRecordEntity record : records
                .findByPoliceVerificationStatusAndPoliceReverifyDueOnLessThanEqualOrderByPoliceReverifyDueOnAsc(
                        VerificationStatus.VERIFIED, today)) {
            LocalDate dueOn = record.getPoliceReverifyDueOn();
            record.setPoliceVerificationStatus(VerificationStatus.PENDING);
            records.save(record);
            audit.record(record.getAccountId(), null, VerificationAudit.ACTOR_SYSTEM, VerificationAudit.POLICE_REVERIFY_DUE,
                    null, null, "Due " + PartnerDocumentService.plain(dueOn));
            events.publish(new VerificationLapsed(record.getAccountId(), VerificationLapsed.Cause.POLICE_REVERIFICATION_DUE,
                    null, dueOn));
            due++;
        }
        List<Integer> thresholds = rules.reminderDays();
        if (!thresholds.isEmpty()) {
            for (VerificationRecordEntity record : records
                    .findByPoliceVerificationStatusAndPoliceReverifyDueOnLessThanEqualOrderByPoliceReverifyDueOnAsc(
                            VerificationStatus.VERIFIED, today.plusDays(thresholds.get(0)))) {
                int daysLeft = (int) ChronoUnit.DAYS.between(today, record.getPoliceReverifyDueOn());
                Integer threshold = null;
                for (int t : thresholds) {
                    if (daysLeft <= t) {
                        threshold = t;
                    }
                }
                Integer sent = record.getPoliceReverifyReminderDays();
                if (threshold == null || (sent != null && sent <= threshold)) {
                    continue;
                }
                record.recordPoliceReverifyReminder(threshold);
                records.save(record);
                events.publish(new PartnerDocumentExpiring(record.getAccountId(), PartnerDocumentType.POLICE_CERTIFICATE,
                        record.getPoliceReverifyDueOn(), daysLeft));
            }
        }
        if (due > 0) {
            log.info("{} police check(s) fell due for re-verification", due);
        }
        return due;
    }

    /** Every decision on her police check, newest first. */
    @Transactional(readOnly = true)
    public List<PoliceVerificationRecord> history(UUID accountId) {
        return checks.findByAccountIdOrderByDecidedAtDesc(accountId).stream().map(PoliceVerificationService::toRecord).toList();
    }

    /** Partners whose police check falls due within this many days, soonest first - the console's queue. */
    @Transactional(readOnly = true)
    public List<VerificationRecordEntity> dueWithin(int days) {
        return records.findByPoliceVerificationStatusAndPoliceReverifyDueOnLessThanEqualOrderByPoliceReverifyDueOnAsc(
                VerificationStatus.VERIFIED, today().plusDays(days));
    }

    /** Account deletion: see PoliceVerificationEntity.eraseForDeletion. */
    @Transactional
    public void eraseFor(UUID accountId) {
        for (PoliceVerificationEntity check : checks.findByAccountIdOrderByDecidedAtDesc(accountId)) {
            check.eraseForDeletion();
            checks.save(check);
        }
    }

    static PoliceVerificationRecord toRecord(PoliceVerificationEntity e) {
        return new PoliceVerificationRecord(e.getId(), e.getAccountId(), e.getOutcome(), e.getPoliceMethod(),
                e.getPoliceCertificateNumber(), e.getPoliceIssuedOn(), e.getPoliceIssuingAuthority(),
                e.getPoliceReverifyDueOn(), e.getPoliceDocumentId(), e.getExtraDocumentId(), e.getPoliceConsentAt(),
                e.getPoliceConsentTextVersion(), e.getRejectionReason(), e.getDecidedBy(), e.getDecidedAt());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
