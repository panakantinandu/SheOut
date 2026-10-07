package com.sheout.driververification.internal;

import com.sheout.driververification.PartnerDocumentType;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A licence or vehicle number a person turned down for fraud or a confirmed
 * mismatch - see V75__flagged_numbers_and_photo_warnings.sql for what is,
 * and is never, remembered.
 */
@Entity
@Table(name = "flagged_document_numbers")
public class FlaggedDocumentNumberEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PartnerDocumentType documentType;

    @Column(nullable = false, length = 64)
    private String documentNumber;

    @Column(nullable = false, length = 40)
    private String reasonCode;

    @Column(nullable = false)
    private UUID accountId;

    private UUID documentId;

    @Column(nullable = false)
    private UUID flaggedBy;

    protected FlaggedDocumentNumberEntity() {
        // JPA
    }

    FlaggedDocumentNumberEntity(PartnerDocumentType documentType, String documentNumber, String reasonCode,
                                UUID accountId, UUID documentId, UUID flaggedBy) {
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.reasonCode = reasonCode;
        this.accountId = accountId;
        this.documentId = documentId;
        this.flaggedBy = flaggedBy;
    }

    public PartnerDocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getFlaggedBy() {
        return flaggedBy;
    }
}
