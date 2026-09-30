package com.sheout.driververification.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One start-of-shift check, from the prompts being issued to the answer.
 * See ShiftCheckService for what each status means.
 */
@Entity
@Table(name = "shift_checks")
class ShiftCheckEntity extends BaseEntity {

    enum Status {
        /** Prompts handed out, nothing sent back yet. */
        ISSUED,
        /** Matched, or passed on the photo alone when her phone could not compare. */
        PASSED,
        /** Did not match, or no face was found. She may try again. */
        RETRY,
        /** Did not match several times running. Blocked until an operator looks. */
        NEEDS_REVIEW,
        /** An operator looked and it is her. Counts as passed. */
        CLEARED,
        /** An operator looked and it is not good enough. She must take a new one. */
        REJECTED
    }

    enum FaceResult {
        MATCH,
        NO_MATCH,
        /** No face in the frame - a hand over the lens, a helmet visor down. */
        NO_FACE,
        /** Her phone could not compare, or there is no verified selfie to compare with. */
        UNAVAILABLE
    }

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false, unique = true, length = 64)
    private String challengeId;

    @Column(nullable = false, length = 100)
    private String prompts;

    @Column(nullable = false)
    private Instant challengeExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    private String selfieKey;
    private String framesKey;
    private String helmetKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FaceResult faceResult;

    private Double faceDistance;
    private Instant submittedAt;
    private Instant reviewedAt;
    private UUID reviewedBy;

    @Column(length = 500)
    private String reviewNote;

    protected ShiftCheckEntity() {
    }

    ShiftCheckEntity(UUID accountId, String challengeId, String prompts, Instant challengeExpiresAt) {
        this.accountId = accountId;
        this.challengeId = challengeId;
        this.prompts = prompts;
        this.challengeExpiresAt = challengeExpiresAt;
        this.status = Status.ISSUED;
    }

    boolean answerable(Instant now) {
        return status == Status.ISSUED && now.isBefore(challengeExpiresAt);
    }

    void recordAnswer(Status outcome, FaceResult face, Double distance,
                      String selfieKey, String framesKey, String helmetKey, Instant now) {
        this.status = outcome;
        this.faceResult = face;
        this.faceDistance = distance;
        this.selfieKey = selfieKey;
        this.framesKey = framesKey;
        this.helmetKey = helmetKey;
        this.submittedAt = now;
    }

    void recordReview(Status decision, UUID adminId, String note, Instant now) {
        this.status = decision;
        this.reviewedBy = adminId;
        this.reviewNote = note;
        this.reviewedAt = now;
    }

    /** Photos gone - account deletion, or past the retention window. The row and its outcome stay. */
    void clearPhotos() {
        this.selfieKey = null;
        this.framesKey = null;
        this.helmetKey = null;
    }

    UUID getAccountId() {
        return accountId;
    }

    String getPrompts() {
        return prompts;
    }

    Status getStatus() {
        return status;
    }

    String getSelfieKey() {
        return selfieKey;
    }

    String getFramesKey() {
        return framesKey;
    }

    String getHelmetKey() {
        return helmetKey;
    }

    FaceResult getFaceResult() {
        return faceResult;
    }

    Double getFaceDistance() {
        return faceDistance;
    }

    Instant getSubmittedAt() {
        return submittedAt;
    }

    Instant getReviewedAt() {
        return reviewedAt;
    }

    String getReviewNote() {
        return reviewNote;
    }
}
