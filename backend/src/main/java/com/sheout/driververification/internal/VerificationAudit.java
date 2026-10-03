package com.sheout.driververification.internal;

import com.sheout.staff.Permission;
import com.sheout.staff.StaffAudit;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.VerificationAuditEntry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The record of who did what to a partner's verification.
 * <p>
 * Written in the same transaction as the thing it records, so a decision
 * cannot land without its line here, or the other way round. Opening a
 * document is recorded too, before the link is handed out: police
 * certificates and background reports are sensitive personal data, and
 * "who has looked at this" must have an answer.
 * <p>
 * The action names are a closed set, written here as constants rather than
 * an enum so a row written by a newer release still reads in an older one.
 */
@Component
public class VerificationAudit {

    public static final String ACTOR_PARTNER = "PARTNER";
    public static final String ACTOR_OPERATOR = "OPERATOR";
    public static final String ACTOR_SYSTEM = "SYSTEM";
    public static final String ACTOR_PROVIDER = "PROVIDER";

    static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
    static final String DOCUMENT_VERIFIED = "DOCUMENT_VERIFIED";
    static final String DOCUMENT_REJECTED = "DOCUMENT_REJECTED";
    static final String DOCUMENT_EXPIRED = "DOCUMENT_EXPIRED";
    static final String DOCUMENT_VIEWED = "DOCUMENT_VIEWED";
    static final String RENEWAL_REJECTED_EARLIER_KEPT = "RENEWAL_REJECTED_EARLIER_KEPT";
    static final String ID_CHECK_VERIFIED = "ID_CHECK_VERIFIED";
    static final String ID_CHECK_REJECTED = "ID_CHECK_REJECTED";
    static final String POLICE_VERIFIED = "POLICE_VERIFIED";
    static final String POLICE_REJECTED = "POLICE_REJECTED";
    static final String POLICE_REVERIFY_DUE = "POLICE_REVERIFY_DUE";
    static final String CONSENT_ACCEPTED = "CONSENT_ACCEPTED";
    static final String BACKGROUND_CHECK_SUBMITTED = "BACKGROUND_CHECK_SUBMITTED";
    static final String PROVIDER_RESULT_RECEIVED = "PROVIDER_RESULT_RECEIVED";

    private final VerificationAuditRepository repository;
    private final StaffAudit staffAudit;

    VerificationAudit(VerificationAuditRepository repository, StaffAudit staffAudit) {
        this.repository = repository;
        this.staffAudit = staffAudit;
    }

    /**
     * Written here, for her verification history, and - when an operator did
     * it - into the staff audit log too, so one log answers "what did this
     * member of staff do", document views included. The staff copy carries no
     * detail text: that can quote her document, and the staff log is read by
     * people with no business reading it.
     */
    void record(UUID accountId, UUID actorId, String actorRole, String action, UUID documentId,
                PartnerDocumentType documentType, String detail) {
        repository.save(new VerificationAuditEntity(accountId, actorId, actorRole, action, documentId, documentType,
                detail, Instant.now()));
        if (ACTOR_OPERATOR.equals(actorRole)) {
            staffAudit.record(new StaffAudit.Entry("verification." + action.toLowerCase(java.util.Locale.ROOT),
                    action.equals(DOCUMENT_VIEWED) ? Permission.DOCUMENTS_VIEW : Permission.VERIFICATION_REVIEW,
                    StaffAudit.Result.OK, documentId != null ? "DOCUMENT" : "ACCOUNT",
                    String.valueOf(documentId != null ? documentId : accountId), null,
                    documentType == null ? null : "{\"documentType\":\"" + documentType + "\",\"accountId\":\"" + accountId + "\"}"));
        }
    }

    /** Account deletion: see VerificationAuditEntity.clearDetail. */
    void eraseDetailsFor(UUID accountId) {
        for (VerificationAuditEntity entry : repository.findByAccountIdOrderByAtDesc(accountId)) {
            entry.clearDetail();
            repository.save(entry);
        }
    }

    List<VerificationAuditEntry> historyFor(UUID accountId) {
        return repository.findByAccountIdOrderByAtDesc(accountId).stream()
                .map(e -> new VerificationAuditEntry(e.getActorId(), e.getActorRole(), e.getAction(), e.getDocumentId(),
                        e.getDocumentType(), e.getDetail(), e.getAt()))
                .toList();
    }
}
