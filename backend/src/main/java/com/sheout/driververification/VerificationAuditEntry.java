package com.sheout.driververification;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of a partner's verification history: who (null for the system),
 * in what capacity, did what, to which document, and when. Includes every
 * time an operator opened one of her documents.
 */
public record VerificationAuditEntry(
        UUID actorId,
        String actorRole,
        String action,
        UUID documentId,
        PartnerDocumentType documentType,
        String detail,
        Instant at
) {
}
