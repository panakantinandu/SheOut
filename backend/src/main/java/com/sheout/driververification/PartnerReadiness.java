package com.sheout.driververification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Whether a partner may take trips right now, and if not, exactly why.
 * <p>
 * One answer for every place that asks - going online, dispatch's offer
 * check, her own checklist and Home banner, the console - so they cannot
 * disagree about her. {@code blockers} are in the order she should fix them;
 * the first is what a refusal tells her. {@code warnings} will block her
 * soon (a document expiring within the reminder window), or would block her
 * if the document rule were being enforced.
 * <p>
 * {@code documents} is every document her vehicle requires, plus the police
 * evidence, each with where it stands - a missing one has a null status.
 */
public record PartnerReadiness(
        boolean ready,
        List<Blocker> blockers,
        List<Blocker> warnings,
        List<DocumentState> documents
) {

    public enum BlockerCode {
        /** Her ID and gender check has not passed. */
        ID_CHECK,
        /** Her police check is not VERIFIED: not yet done, or rejected. */
        POLICE_CHECK,
        /** Her police check came due again on {@code date}. As a warning: it falls due on {@code date}. */
        POLICE_REVERIFY_DUE,
        /** She has not agreed to the current verification consent. */
        CONSENT_REQUIRED,
        DOCUMENT_MISSING,
        DOCUMENT_UNDER_REVIEW,
        DOCUMENT_REJECTED,
        /** Expired on {@code date}. */
        DOCUMENT_EXPIRED,
        /** Expires on {@code date}, within the reminder window. Only ever a warning. */
        DOCUMENT_EXPIRING,
        /** Her policy is not for commercial use, and she carries paying passengers. */
        INSURANCE_NOT_COMMERCIAL
    }

    /**
     * One reason. {@code message} is plain English for the server's own
     * refusal; the apps build their own sentence in her language from code,
     * documentType and date.
     */
    public record Blocker(BlockerCode code, PartnerDocumentType documentType, LocalDate date, String message) {
    }

    public record DocumentState(
            PartnerDocumentType type,
            /** Required for her vehicle. False for police evidence, which her police check covers. */
            boolean required,
            /** Null when nothing is on file. */
            PartnerDocumentStatus status,
            String documentNumber,
            LocalDate issuedOn,
            LocalDate validUntil,
            String rejectionReason,
            InsuranceUseType insuranceUseType,
            Instant submittedAt,
            /** A newer upload is waiting for review while this one still counts. */
            boolean renewalUnderReview
    ) {
    }

    /** The first reason she cannot work, or null when she can. */
    public Blocker firstBlocker() {
        return blockers.isEmpty() ? null : blockers.get(0);
    }
}
