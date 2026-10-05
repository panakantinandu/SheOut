package com.sheout.driververification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One document, as an operator sees it in a queue or on her Documents tab.
 * Carries no storage key or URL: opening the file goes through
 * VerificationApi.openDocument, which records who looked.
 */
public record PartnerDocumentSummary(
        UUID id,
        UUID accountId,
        PartnerDocumentType type,
        PartnerDocumentStatus status,
        String documentNumber,
        LocalDate issuedOn,
        LocalDate validUntil,
        String rejectionReason,
        InsuranceUseType insuranceUseType,
        PartnerDocumentSource source,
        String providerReference,
        boolean fileOnFile,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        Instant supersededAt,
        UUID replacesDocumentId,
        /** An RC's owner, as she said (null for other documents, or an RC sent before she was asked). */
        VehicleOwner vehicleOwner,
        /** The owner's name as printed on the RC, when it is not hers. */
        String ownerName
) {

    public PartnerDocumentSummary(UUID id, UUID accountId, PartnerDocumentType type, PartnerDocumentStatus status,
                                  String documentNumber, LocalDate issuedOn, LocalDate validUntil, String rejectionReason,
                                  InsuranceUseType insuranceUseType, PartnerDocumentSource source, String providerReference,
                                  boolean fileOnFile, Instant submittedAt, Instant reviewedAt, UUID reviewedBy,
                                  Instant supersededAt, UUID replacesDocumentId) {
        this(id, accountId, type, status, documentNumber, issuedOn, validUntil, rejectionReason, insuranceUseType, source,
                providerReference, fileOnFile, submittedAt, reviewedAt, reviewedBy, supersededAt, replacesDocumentId, null, null);
    }
}
