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
        UUID replacesDocumentId
) {
}
