package com.sheout.sharedkernel.storage;

import java.util.UUID;

/**
 * The swap point for where ID documents (Aadhaar today, potentially other
 * KYC documents later) actually live. VerificationService depends only on
 * this interface - moving from local disk to S3, or from S3 to a different
 * bucket/provider, or later replacing manual Aadhaar review with a
 * third-party KYC API call, only ever means changing (or adding) an
 * implementation here, never touching the review workflow in
 * VerificationService.
 */
public interface DocumentStorage {

    /** Stores the document and returns an opaque key to retrieve it later - never a public URL by itself. */
    String store(UUID accountId, String documentType, DocumentUpload upload);

    /** Resolves a stored key to something an admin reviewer can open. */
    String resolveUrl(String storageKey);

    /**
     * Removes the stored object itself - not a flag, not a soft delete. Used
     * when an account is deleted, where an ID document left in a bucket is a
     * document the person was told was gone.
     * <p>
     * Idempotent: deleting a key that is already absent is not an error, so
     * a deletion retried after a failure elsewhere completes rather than
     * stopping on the files it already removed. Any other failure throws, so
     * the account deletion that called it rolls back instead of reporting
     * success with the document still stored.
     */
    void delete(String storageKey);
}
