package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.driververification.VerificationStatus;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per account, created automatically when auth publishes
 * AccountRegistered. accountId is a plain UUID column, not a JPA
 * relationship/foreign key into auth's table - this module never touches
 * auth's schema directly, only auth's public API and events.
 */
@Entity
@Table(name = "verification_records")
public class VerificationRecordEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus genderVerificationStatus;

    /** Null for CUSTOMER accounts - police verification is driver-only. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VerificationStatus policeVerificationStatus;

    /** Object storage key for the uploaded Aadhaar document, once submitted. */
    @Column(length = 500)
    private String aadhaarDocumentKey;

    /**
     * When the document was sent in, as opposed to when it was looked at.
     * Null until something is submitted. Re-submitting after a rejection
     * moves it: the queue started waiting again at that moment.
     */
    private Instant documentSubmittedAt;

    /** The live selfie and its liveness frames - see V34__live_selfie.sql. */
    @Column(length = 500)
    private String selfieDocumentKey;
    @Column(length = 500)
    private String livenessFramesKey;
    @Column(length = 200)
    private String selfiePrompts;
    private Instant selfieCapturedAt;

    /** The challenge issued and not yet used. */
    @Column(length = 64)
    private String selfieChallengeNonce;
    @Column(length = 200)
    private String selfieChallengePrompts;
    private Instant selfieChallengeExpiresAt;

    @Column(length = 100)
    private String reviewedBy;
    private Instant reviewedAt;
    @Column(length = 1000)
    private String rejectionReason;

    /**
     * The verification consent she last agreed to, and when - see
     * PoliceVerificationService.acceptConsent. A newer version of the wording
     * asks again. Each acceptance is also a line in her audit trail.
     */
    @Column(length = 40)
    private String consentVersion;
    private Instant consentAcceptedAt;

    /**
     * When her police check must be redone: the issue date of the evidence
     * plus POLICE_REVERIFY_MONTHS, or what the operator set. On that day the
     * sweep puts police_verification_status back to PENDING.
     */
    private java.time.LocalDate policeReverifyDueOn;
    /** The smallest "days before" reminder already sent for this due date. */
    private Integer policeReverifyReminderDays;

    protected VerificationRecordEntity() {
        // JPA
    }

    public VerificationRecordEntity(UUID accountId, AccountRole role) {
        this.accountId = accountId;
        this.role = role;
        this.genderVerificationStatus = VerificationStatus.PENDING;
        this.policeVerificationStatus = role == AccountRole.DRIVER ? VerificationStatus.PENDING : null;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public AccountRole getRole() {
        return role;
    }

    public VerificationStatus getGenderVerificationStatus() {
        return genderVerificationStatus;
    }

    public void setGenderVerificationStatus(VerificationStatus status) {
        this.genderVerificationStatus = status;
    }

    public VerificationStatus getPoliceVerificationStatus() {
        return policeVerificationStatus;
    }

    public void setPoliceVerificationStatus(VerificationStatus status) {
        this.policeVerificationStatus = status;
    }

    public String getAadhaarDocumentKey() {
        return aadhaarDocumentKey;
    }

    public void setAadhaarDocumentKey(String key) {
        this.aadhaarDocumentKey = key;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    /** For account deletion only - the note is free text about the person. See AccountDeletionDocumentListener. */
    public void clearRejectionReason() {
        this.rejectionReason = null;
    }

    public void markDocumentSubmitted() {
        this.documentSubmittedAt = Instant.now();
    }

    /** A fresh challenge replaces any earlier one: only the latest can be answered. */
    public void issueSelfieChallenge(String nonce, String prompts, Instant expiresAt) {
        this.selfieChallengeNonce = nonce;
        this.selfieChallengePrompts = prompts;
        this.selfieChallengeExpiresAt = expiresAt;
    }

    /** The prompts of the challenge this nonce answers, if it is live; used up either way it matches. */
    public java.util.Optional<String> consumeSelfieChallenge(String nonce, Instant now) {
        if (nonce == null || selfieChallengeNonce == null || !selfieChallengeNonce.equals(nonce)
                || selfieChallengeExpiresAt == null || now.isAfter(selfieChallengeExpiresAt)) {
            return java.util.Optional.empty();
        }
        String prompts = selfieChallengePrompts;
        this.selfieChallengeNonce = null;
        this.selfieChallengePrompts = null;
        this.selfieChallengeExpiresAt = null;
        return java.util.Optional.of(prompts);
    }

    public void recordLiveSelfie(String selfieKey, String framesKey, String prompts, Instant at) {
        this.selfieDocumentKey = selfieKey;
        this.livenessFramesKey = framesKey;
        this.selfiePrompts = prompts;
        this.selfieCapturedAt = at;
    }

    public void clearLiveSelfie() {
        recordLiveSelfie(null, null, null, null);
    }

    public String getSelfieDocumentKey() {
        return selfieDocumentKey;
    }

    public String getLivenessFramesKey() {
        return livenessFramesKey;
    }

    public String getSelfiePrompts() {
        return selfiePrompts;
    }

    public Instant getSelfieCapturedAt() {
        return selfieCapturedAt;
    }

    public Instant getDocumentSubmittedAt() {
        return documentSubmittedAt;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public Instant getConsentAcceptedAt() {
        return consentAcceptedAt;
    }

    void recordConsent(String version, Instant at) {
        this.consentVersion = version;
        this.consentAcceptedAt = at;
    }

    public java.time.LocalDate getPoliceReverifyDueOn() {
        return policeReverifyDueOn;
    }

    public Integer getPoliceReverifyReminderDays() {
        return policeReverifyReminderDays;
    }

    /** A new due date restarts its reminders. */
    void setPoliceReverifyDueOn(java.time.LocalDate dueOn) {
        this.policeReverifyDueOn = dueOn;
        this.policeReverifyReminderDays = null;
    }

    void recordPoliceReverifyReminder(int days) {
        this.policeReverifyReminderDays = days;
    }

    public void recordReview(String reviewedByAccountId, String rejectionReason) {
        this.reviewedBy = reviewedByAccountId;
        this.reviewedAt = Instant.now();
        this.rejectionReason = rejectionReason;
    }

    /**
     * Per the flagged assumption on what "verification completes" means:
     * for a customer, gender verification alone; for a driver, gender AND
     * police verification both VERIFIED.
     */
    public boolean isFullyVerified() {
        boolean genderVerified = genderVerificationStatus == VerificationStatus.VERIFIED;
        if (role != AccountRole.DRIVER) {
            return genderVerified;
        }
        return genderVerified && policeVerificationStatus == VerificationStatus.VERIFIED;
    }
}
