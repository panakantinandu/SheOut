package com.sheout.admin.internal;

import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerReadiness;
import com.sheout.driververification.PoliceVerificationRecord;
import com.sheout.driververification.VerificationConsentStatus;
import com.sheout.driververification.VerificationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Everything on a partner's Documents tab, in one response: may she work
 * and why not, every document and every version of it, her police checks
 * and their evidence, her consent, and who did what - including every time
 * somebody opened one of her documents.
 * <p>
 * No file URLs. Opening a file goes through driver-verification's link
 * endpoint, which records the view; a URL here would be a view nobody
 * recorded.
 */
public record PartnerVerificationView(
        UUID accountId,
        String name,
        String phoneNumber,
        String vehicleType,
        String vehicleRegistrationNumber,
        VerificationStatus genderVerificationStatus,
        VerificationStatus policeVerificationStatus,
        LocalDate policeReverifyDueOn,
        PartnerReadiness readiness,
        List<PartnerDocumentSummary> documents,
        List<PoliceVerificationRecord> policeChecks,
        VerificationConsentStatus consent,
        List<AuditRow> audit,
        /** POLICE_REVERIFY_MONTHS, so the form can work out the default re-verify date the server will use. */
        int policeReverifyMonths,
        /**
         * Her date of birth, which the government licence lookup (Sarathi) asks
         * for with the licence number. The reviewer reads it off the licence
         * she opens anyway; it is here so the check needs no retyping.
         */
        LocalDate dateOfBirth,
        /** DRIVER or CUSTOMER: a rider is reviewed on the same screen, for her ID and selfie only. */
        com.sheout.auth.AccountRole role,
        /** Why her ID was sent back, when it was, and the reason's code. */
        String idRejectionReason,
        String idRejectionCode,
        /** Every check, worked out now, with what the automatic checks noticed. */
        com.sheout.driververification.VerificationChecks checks,
        /** Her name everywhere it is written, against her profile. */
        List<com.sheout.driververification.NameOnFile> names
) {

    /** One audit line, with the operator who acted named by their phone. */
    public record AuditRow(String actor, String actorRole, String action, String documentType, UUID documentId,
                           String detail, Instant at) {
    }
}
