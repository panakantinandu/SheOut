package com.sheout.sharedkernel.storage;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** One uploaded file held in Postgres - see DatabaseDocumentStorage. */
@Entity
@Table(name = "stored_documents")
public class StoredDocumentEntity extends BaseEntity {

    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    protected StoredDocumentEntity() {
        // JPA
    }

    StoredDocumentEntity(String storageKey, UUID accountId, String contentType, byte[] content) {
        this.storageKey = storageKey;
        this.accountId = accountId;
        this.contentType = contentType;
        this.sizeBytes = content.length;
        this.content = content;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }
}
