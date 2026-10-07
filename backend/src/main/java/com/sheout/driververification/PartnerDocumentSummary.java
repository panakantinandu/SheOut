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
        String ownerName,
        /** A licence: the name she typed as printed on it, or null. */
        String nameOnDocument,
        /** A licence: she said the name is the same as her profile, instead of typing it. */
        boolean nameDeclared,
        /** What her phone said about the photo when she sent it anyway (BLURRY, GLARE, DARK, NO_FACE), or null. */
        String photoWarnings,
        /** Why a person sent it back, from RejectionReason. Null otherwise, and on rows from before the list. */
        String rejectionCode
) {

    public PartnerDocumentSummary(UUID id, UUID accountId, PartnerDocumentType type, PartnerDocumentStatus status,
                                  String documentNumber, LocalDate issuedOn, LocalDate validUntil, String rejectionReason,
                                  InsuranceUseType insuranceUseType, PartnerDocumentSource source, String providerReference,
                                  boolean fileOnFile, Instant submittedAt, Instant reviewedAt, UUID reviewedBy,
                                  Instant supersededAt, UUID replacesDocumentId, VehicleOwner vehicleOwner, String ownerName) {
        this(id, accountId, type, status, documentNumber, issuedOn, validUntil, rejectionReason, insuranceUseType, source,
                providerReference, fileOnFile, submittedAt, reviewedAt, reviewedBy, supersededAt, replacesDocumentId,
                vehicleOwner, ownerName, null, false, null, null);
    }

    public PartnerDocumentSummary(UUID id, UUID accountId, PartnerDocumentType type, PartnerDocumentStatus status,
                                  String documentNumber, LocalDate issuedOn, LocalDate validUntil, String rejectionReason,
                                  InsuranceUseType insuranceUseType, PartnerDocumentSource source, String providerReference,
                                  boolean fileOnFile, Instant submittedAt, Instant reviewedAt, UUID reviewedBy,
                                  Instant supersededAt, UUID replacesDocumentId) {
        this(id, accountId, type, status, documentNumber, issuedOn, validUntil, rejectionReason, insuranceUseType, source,
                providerReference, fileOnFile, submittedAt, reviewedAt, reviewedBy, supersededAt, replacesDocumentId, null, null);
    }
}
