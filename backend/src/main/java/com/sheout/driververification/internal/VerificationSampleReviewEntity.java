package com.sheout.driververification.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** A second reviewer's look at one sampled account - see V76__rejection_codes_and_sample_reviews.sql. */
@Entity
@Table(name = "verification_sample_reviews")
public class VerificationSampleReviewEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 8)
    private String sampleWeek;

    @Column(nullable = false)
    private UUID reviewedBy;

    @Column(nullable = false, length = 10)
    private String outcome;

    @Column(length = 1000)
    private String note;

    protected VerificationSampleReviewEntity() {
        // JPA
    }

    VerificationSampleReviewEntity(UUID accountId, String sampleWeek, UUID reviewedBy, String outcome, String note) {
        this.accountId = accountId;
        this.sampleWeek = sampleWeek;
        this.reviewedBy = reviewedBy;
        this.outcome = outcome;
        this.note = note;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSampleWeek() {
        return sampleWeek;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getNote() {
        return note;
    }
}
