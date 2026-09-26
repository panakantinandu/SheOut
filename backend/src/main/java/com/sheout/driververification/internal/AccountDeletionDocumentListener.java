package com.sheout.driververification.internal;

import com.sheout.privacy.AccountDeletionRequested;
import com.sheout.sharedkernel.storage.DocumentStorage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a deleted account's identity documents from storage - the objects
 * themselves - and drops the keys that pointed at them.
 * <p>
 * The verification record stays with its statuses: whether an account was
 * verified is part of the record of the trips it took. The reviewer's
 * rejection note goes, because it is free text about the person.
 * <p>
 * Storage deletes happen inside the deletion's transaction, before commit.
 * If a later step fails and the whole deletion rolls back, the documents are
 * still gone and the record's keys point at nothing; the account holder
 * retries, and DocumentStorage.delete is idempotent, so the retry completes.
 * Erring that way - a document removed, an account not yet deleted - is the
 * acceptable failure. The opposite, reporting a completed deletion with the
 * documents still stored, is not.
 */
@Component
class AccountDeletionDocumentListener {

    private final VerificationRecordRepository records;
    private final DocumentStorage documentStorage;

    AccountDeletionDocumentListener(VerificationRecordRepository records, DocumentStorage documentStorage) {
        this.records = records;
        this.documentStorage = documentStorage;
    }

    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        records.findByAccountId(event.accountId()).ifPresent(record -> {
            if (record.getAadhaarDocumentKey() != null) {
                documentStorage.delete(record.getAadhaarDocumentKey());
                record.setAadhaarDocumentKey(null);
            }
            if (record.getRcDocumentKey() != null) {
                documentStorage.delete(record.getRcDocumentKey());
                record.setRcDocumentKey(null);
            }
            // Her face, twice over: the selfie and the prompt frames go too.
            if (record.getSelfieDocumentKey() != null) {
                documentStorage.delete(record.getSelfieDocumentKey());
            }
            if (record.getLivenessFramesKey() != null) {
                documentStorage.delete(record.getLivenessFramesKey());
            }
            record.clearLiveSelfie();
            record.clearRejectionReason();
            // A submission waiting for review has nothing left to review. Back
            // to "not submitted" takes it off the operators' queue, which
            // would otherwise show a Deleted User with no document to open.
            if (record.getGenderVerificationStatus() == com.sheout.driververification.VerificationStatus.UNDER_REVIEW) {
                record.setGenderVerificationStatus(com.sheout.driververification.VerificationStatus.PENDING);
            }
            records.save(record);
        });
    }
}
