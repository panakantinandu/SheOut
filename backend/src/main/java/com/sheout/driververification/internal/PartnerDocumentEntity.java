package com.sheout.driververification.internal;

import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One document a partner (or an operator, or a provider) put on file - see
 * V55__partner_documents.sql for the table and the "one current row per
 * type" rule.
 * <p>
 * Rows are never overwritten by a new upload. A renewal supersedes the row
 * before it, and the history of who approved what stays readable.
 */
@Entity
@Table(name = "partner_documents")
public class PartnerDocumentEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PartnerDocumentType type;

    @Column(length = 64)
    private String documentNumber;

    private LocalDate issuedOn;

    private LocalDate validUntil;

    @Column(length = 500)
    private String documentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerDocumentStatus status;

    @Column(length = 1000)
    private String rejectionReason;

    private UUID reviewedBy;
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartnerDocumentSource source;

    @Column(length = 200)
    private String providerReference;

    @Column(columnDefinition = "text")
    private String metadataJson;

    private Instant submittedAt;
    private Instant expiredAt;
    private Integer lastReminderDays;
    private UUID replacesDocumentId;
    private Instant supersededAt;

    protected PartnerDocumentEntity() {
        // JPA
    }

    PartnerDocumentEntity(UUID accountId, PartnerDocumentType type, PartnerDocumentSource source,
                          PartnerDocumentStatus status, Instant submittedAt) {
        this.accountId = accountId;
        this.type = type;
        this.source = source;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public PartnerDocumentType getType() {
        return type;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    void setIssuedOn(LocalDate issuedOn) {
        this.issuedOn = issuedOn;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    /** A new date is a new expiry: the reminders already sent were about the old one. */
    void setValidUntil(LocalDate validUntil) {
        if (validUntil == null ? this.validUntil != null : !validUntil.equals(this.validUntil)) {
            this.lastReminderDays = null;
        }
        this.validUntil = validUntil;
    }

    public String getDocumentKey() {
        return documentKey;
    }

    void setDocumentKey(String documentKey) {
        this.documentKey = documentKey;
    }

    public PartnerDocumentStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public PartnerDocumentSource getSource() {
        return source;
    }

    public String getProviderReference() {
        return providerReference;
    }

    void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public Integer getLastReminderDays() {
        return lastReminderDays;
    }

    void recordReminder(int days) {
        this.lastReminderDays = days;
    }

    public UUID getReplacesDocumentId() {
        return replacesDocumentId;
    }

    void setReplacesDocumentId(UUID replacesDocumentId) {
        this.replacesDocumentId = replacesDocumentId;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public boolean isCurrent() {
        return supersededAt == null;
    }

    void supersede(Instant at) {
        this.supersededAt = at;
    }

    /** Back to being the one that counts - a renewal that replaced it was turned down. */
    void reinstate() {
        this.supersededAt = null;
    }

    void markVerified(UUID operatorId, Instant at) {
        this.status = PartnerDocumentStatus.VERIFIED;
        this.reviewedBy = operatorId;
        this.reviewedAt = at;
        this.rejectionReason = null;
    }

    void markRejected(UUID operatorId, Instant at, String reason) {
        this.status = PartnerDocumentStatus.REJECTED;
        this.reviewedBy = operatorId;
        this.reviewedAt = at;
        this.rejectionReason = reason;
    }

    void markExpired(Instant at) {
        this.status = PartnerDocumentStatus.EXPIRED;
        this.expiredAt = at;
    }

    /** A provider's answer arrived and there is now something to look at. */
    void markUnderReview() {
        this.status = PartnerDocumentStatus.UNDER_REVIEW;
    }

    /**
     * For account deletion: the file is gone, and so is everything on the
     * row that is about her rather than about the decision - the number, the
     * provider's reference, the facts read off the document, the note.
     */
    void eraseForDeletion() {
        this.documentKey = null;
        this.documentNumber = null;
        this.providerReference = null;
        this.metadataJson = null;
        this.rejectionReason = null;
    }
}
