package com.sheout.driververification.internal;

import com.sheout.driververification.CourtRejectionCategory;
import com.sheout.driververification.CourtScreeningDecision;
import com.sheout.driververification.CourtScreeningStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * One court-record screening of one partner. The rules for moving it along
 * live in CourtScreeningService; this only holds the state.
 */
@Entity
@Table(name = "court_screenings")
public class CourtScreeningEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CourtScreeningStatus status;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(length = 40)
    private String consentVersion;
    private Instant consentAt;

    @Column(length = 1000)
    private String nameVariants;
    @Column(length = 150)
    private String relativeName;
    @Column(length = 1000)
    private String districts;

    private UUID flaggedBy;
    private UUID secondReviewer;
    @Column(length = 20)
    private String secondOutcome;
    @Column(length = 1000)
    private String secondNote;
    private Instant secondAt;

    private Instant partnerNotifiedAt;
    private Instant responseDueAt;
    @Column(length = 1000)
    private String partnerResponse;
    private Instant partnerRespondedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private CourtScreeningDecision decision;
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private CourtRejectionCategory reasonCategory;
    @Column(length = 1000)
    private String decisionNote;
    private UUID decidedBy;
    private Instant decidedAt;

    private LocalDate pccIssuedOn;
    @Column(length = 200)
    private String pccAuthority;
    private LocalDate pccValidUntil;
    private UUID pccDocumentId;

    private Instant leftAt;
    private Instant anonymisedAt;

    protected CourtScreeningEntity() {
    }

    CourtScreeningEntity(UUID accountId, UUID requestedBy, List<String> districts) {
        this.accountId = accountId;
        this.requestedBy = requestedBy;
        this.status = CourtScreeningStatus.CONSENT_REQUESTED;
        this.districts = join(districts);
    }

    static String join(List<String> values) {
        if (values == null) {
            return null;
        }
        String joined = String.join("\n", values.stream().map(String::trim).filter(v -> !v.isEmpty()).distinct().toList());
        return joined.isEmpty() ? null : joined;
    }

    static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split("\n")).filter(v -> !v.isBlank()).toList();
    }

    void consent(String version, Instant at) {
        this.consentVersion = version;
        this.consentAt = at;
        this.status = CourtScreeningStatus.SEARCHING;
    }

    void withdraw() {
        this.status = CourtScreeningStatus.WITHDRAWN;
    }

    void subject(List<String> nameVariants, String relativeName, List<String> districts) {
        this.nameVariants = join(nameVariants);
        this.relativeName = relativeName == null || relativeName.isBlank() ? null : relativeName.trim();
        if (districts != null) {
            this.districts = join(districts);
        }
    }

    void flag(UUID reviewer) {
        if (this.flaggedBy == null) {
            this.flaggedBy = reviewer;
        }
        this.status = CourtScreeningStatus.MATCH_TO_CONFIRM;
    }

    void secondSignOff(UUID reviewer, boolean agree, String note, Instant at) {
        this.secondReviewer = reviewer;
        this.secondOutcome = agree ? "AGREE" : "DISAGREE";
        this.secondNote = note;
        this.secondAt = at;
        this.status = agree ? CourtScreeningStatus.MATCH_CONFIRMED : CourtScreeningStatus.SEARCHING;
    }

    void notifyPartner(Instant at, Instant dueAt) {
        this.partnerNotifiedAt = at;
        this.responseDueAt = dueAt;
        this.status = CourtScreeningStatus.AWAITING_RESPONSE;
    }

    void partnerResponded(String text, Instant at) {
        this.partnerResponse = text;
        this.partnerRespondedAt = at;
        this.status = CourtScreeningStatus.RESPONSE_RECEIVED;
    }

    /** Asking for a certificate is telling her: it opens the same response window. */
    void askForPcc(Instant at, Instant dueAt) {
        this.partnerNotifiedAt = at;
        this.responseDueAt = dueAt;
        this.status = CourtScreeningStatus.AWAITING_PCC;
    }

    void recordPcc(LocalDate issuedOn, String authority, LocalDate validUntil, UUID documentId) {
        this.pccIssuedOn = issuedOn;
        this.pccAuthority = authority;
        this.pccValidUntil = validUntil;
        this.pccDocumentId = documentId;
    }

    void decide(CourtScreeningDecision decision, CourtRejectionCategory category, String note, UUID by, Instant at) {
        this.decision = decision;
        this.reasonCategory = category;
        this.decisionNote = note;
        this.decidedBy = by;
        this.decidedAt = at;
        this.status = switch (decision) {
            case APPROVE -> CourtScreeningStatus.APPROVED;
            case APPROVE_WITH_NOTE -> CourtScreeningStatus.APPROVED_WITH_NOTE;
            case REJECT -> CourtScreeningStatus.REJECTED;
            case ASK_FOR_PCC -> CourtScreeningStatus.AWAITING_PCC;
        };

    }

    void markLeft(Instant at) {
        if (this.leftAt == null) {
            this.leftAt = at;
        }
    }

    /** Retention: everything that identifies her or anyone else goes; the outcome and dates stay. */
    void anonymise(Instant at) {
        this.nameVariants = null;
        this.relativeName = null;
        this.districts = null;
        this.secondNote = null;
        this.partnerResponse = null;
        this.decisionNote = null;
        this.pccAuthority = null;
        this.pccDocumentId = null;
        this.anonymisedAt = at;
    }

    public UUID getAccountId() { return accountId; }
    public CourtScreeningStatus getStatus() { return status; }
    public UUID getRequestedBy() { return requestedBy; }
    public String getConsentVersion() { return consentVersion; }
    public Instant getConsentAt() { return consentAt; }
    public List<String> getNameVariants() { return split(nameVariants); }
    public String getRelativeName() { return relativeName; }
    public List<String> getDistricts() { return split(districts); }
    public UUID getFlaggedBy() { return flaggedBy; }
    public UUID getSecondReviewer() { return secondReviewer; }
    public String getSecondOutcome() { return secondOutcome; }
    public String getSecondNote() { return secondNote; }
    public Instant getSecondAt() { return secondAt; }
    public Instant getPartnerNotifiedAt() { return partnerNotifiedAt; }
    public Instant getResponseDueAt() { return responseDueAt; }
    public String getPartnerResponse() { return partnerResponse; }
    public Instant getPartnerRespondedAt() { return partnerRespondedAt; }
    public CourtScreeningDecision getDecision() { return decision; }
    public CourtRejectionCategory getReasonCategory() { return reasonCategory; }
    public String getDecisionNote() { return decisionNote; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public LocalDate getPccIssuedOn() { return pccIssuedOn; }
    public String getPccAuthority() { return pccAuthority; }
    public LocalDate getPccValidUntil() { return pccValidUntil; }
    public UUID getPccDocumentId() { return pccDocumentId; }
    public Instant getLeftAt() { return leftAt; }
    public Instant getAnonymisedAt() { return anonymisedAt; }
}
