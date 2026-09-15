package com.sheout.sharedkernel.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Documents stored in Postgres, the default.
 * <p>
 * WHY NOT DISK. Render's container filesystem, which LocalDiskDocumentStorage
 * wrote to, is wiped on every restart and redeploy, and a file on it has no
 * address a phone can load. In production that meant no profile photo ever
 * displayed, and every upload was eventually lost while its record still
 * claimed it existed. The database is the one store this service already has
 * that survives a deploy, and it needs no new account or vendor.
 * <p>
 * The trade-off is size: Postgres is not built to hold large binaries. Photos
 * are downscaled on the phone before upload, and uploads are capped at 10MB by
 * the multipart limit. S3DocumentStorage remains the swap when volume
 * justifies a bucket - set DOCUMENT_STORAGE_PROVIDER=s3.
 */
@Component
@ConditionalOnProperty(name = "sheout.document-storage.provider", havingValue = "database", matchIfMissing = true)
public class DatabaseDocumentStorage implements DocumentStorage {

    private final StoredDocumentRepository documents;
    private final DocumentLinkSigner links;

    public DatabaseDocumentStorage(StoredDocumentRepository documents, DocumentLinkSigner links) {
        this.documents = documents;
        this.links = links;
    }

    @Override
    @Transactional
    public String store(UUID accountId, String documentType, DocumentUpload upload) {
        String key = DocumentKeys.build(accountId, documentType, upload.filename());
        String contentType = upload.contentType() == null || upload.contentType().isBlank()
                ? "application/octet-stream" : upload.contentType();
        documents.save(new StoredDocumentEntity(key, accountId, contentType, upload.content()));
        return key;
    }

    /** A signed, expiring link served by StoredDocumentController - loadable by an img tag or a browser tab. */
    @Override
    public String resolveUrl(String storageKey) {
        return links.linkFor(storageKey);
    }

    @Override
    @Transactional
    public void delete(String storageKey) {
        documents.deleteByStorageKey(storageKey);
    }
}
