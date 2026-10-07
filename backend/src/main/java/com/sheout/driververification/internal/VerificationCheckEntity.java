package com.sheout.driververification.internal;

import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;
import com.sheout.driververification.internal.checks.CheckProvider;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One check on one account - see V74__verification_checks.sql.
 * <p>
 * Every change of status says who made it and when (decidedBy, null for the
 * software; decidedByRole; decidedAt), and is also a line in her
 * verification audit trail.
 */
@Entity
@Table(name = "verification_checks")
public class VerificationCheckEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false, length = 30)
    private CheckType type;

    @Column(nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckStatus status;

    @Column(length = 300)
    private String reason;

    @Column(columnDefinition = "text")
    private String findingsJson;

    @Column(length = 40)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CheckStatus providerStatus;

    @Column(length = 300)
    private String providerReason;

    @Column(length = 200)
    private String providerReference;

    private UUID subjectId;

    @Column(length = 64)
    private String subjectVersion;

    private UUID decidedBy;

    @Column(nullable = false, length = 20)
    private String decidedByRole;

    @Column(nullable = false)
    private Instant decidedAt;

    @Column(length = 1000)
    private String overrideNote;
    private UUID overrideRequestedBy;
    private UUID overrideApprovedBy;

    protected VerificationCheckEntity() {
        // JPA
    }

    VerificationCheckEntity(UUID accountId, CheckType type, Instant at) {
        this.accountId = accountId;
        this.type = type;
        this.status = CheckStatus.PENDING;
        this.decidedByRole = "SYSTEM";
        this.decidedAt = at;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public CheckType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    void setRequired(boolean required) {
        this.required = required;
    }

    public CheckStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    void setFindingsJson(String findingsJson) {
        this.findingsJson = findingsJson;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public CheckStatus getProviderStatus() {
        return providerStatus;
    }

    public String getProviderReason() {
        return providerReason;
    }

    void recordVerdict(String provider, CheckProvider.Verdict verdict) {
        this.provider = provider;
        this.providerStatus = verdict.status();
        String why = verdict.reason();
        this.providerReason = why == null ? null : why.length() > 300 ? why.substring(0, 300) : why;
        this.providerReference = verdict.reference();
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getSubjectVersion() {
        return subjectVersion;
    }

    /** A new thing to check: whatever was decided about the old one no longer applies. */
    void newSubject(UUID subjectId, String subjectVersion) {
        this.subjectId = subjectId;
        this.subjectVersion = subjectVersion;
        this.status = CheckStatus.PENDING;
        this.providerStatus = null;
        this.providerReason = null;
        this.providerReference = null;
        this.overrideNote = null;
        this.overrideRequestedBy = null;
        this.overrideApprovedBy = null;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public String getDecidedByRole() {
        return decidedByRole;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getOverrideNote() {
        return overrideNote;
    }

    public UUID getOverrideRequestedBy() {
        return overrideRequestedBy;
    }

    public UUID getOverrideApprovedBy() {
        return overrideApprovedBy;
    }

    void decide(CheckStatus status, String reason, UUID by, String byRole, Instant at) {
        this.status = status;
        this.reason = reason == null ? null : reason.length() > 300 ? reason.substring(0, 300) : reason;
        this.decidedBy = by;
        this.decidedByRole = byRole;
        this.decidedAt = at;
    }

    void recordOverride(String note, UUID requestedBy, UUID approvedBy) {
        this.overrideNote = note;
        this.overrideRequestedBy = requestedBy;
        this.overrideApprovedBy = approvedBy;
    }

    /** Account deletion: what was typed about her goes; the decision and who made it stay. */
    void eraseForDeletion() {
        this.findingsJson = null;
        this.overrideNote = null;
        this.providerReference = null;
        this.providerReason = null;
        this.reason = null;
    }
}
