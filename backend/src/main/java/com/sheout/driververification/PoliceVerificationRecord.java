package com.sheout.driververification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.sheout.sharedkernel.privacy.Pii;

/**
 * One recorded police-check decision and what it rested on. A partner has
 * one per check; re-verification adds a new one rather than overwriting.
 * Null method/number/dates/documents on a rejection.
 */
public record PoliceVerificationRecord(
        UUID id,
        UUID accountId,
        VerificationStatus outcome,
        PoliceVerificationMethod method,
        @Pii(Pii.Kind.DOCUMENT) String certificateNumber,
        LocalDate issuedOn,
        String issuingAuthority,
        LocalDate reverifyDueOn,
        UUID evidenceDocumentId,
        UUID extraEvidenceDocumentId,
        Instant consentAt,
        String consentTextVersion,
        String rejectionReason,
        UUID decidedBy,
        Instant decidedAt
) {
}
