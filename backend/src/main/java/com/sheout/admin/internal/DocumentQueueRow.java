package com.sheout.admin.internal;

import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the documents queues: a partner and one thing about her
 * paperwork that needs a person - a document to read, one running out, one
 * that ran out, or a police check falling due. For a police check
 * documentId is null, documentType is POLICE_CERTIFICATE and validUntil is
 * the date it falls due.
 * <p>
 * daysLeft is from today in India: negative once it has passed.
 */
public record DocumentQueueRow(
        UUID accountId,
        String name,
        String photoUrl,
        String phoneNumber,
        String vehicleType,
        String vehicleRegistrationNumber,
        UUID documentId,
        PartnerDocumentType documentType,
        PartnerDocumentStatus status,
        String documentNumber,
        LocalDate validUntil,
        Long daysLeft,
        Instant submittedAt,
        boolean online,
        boolean blocked
) {
}
