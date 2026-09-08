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

    @Column(length = 100)
    private String reviewedBy;
    private Instant reviewedAt;
    @Column(length = 1000)
    private String rejectionReason;

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
