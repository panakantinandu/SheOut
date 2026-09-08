package com.sheout.driververification.internal.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Dev-only implementation - writes to a local directory. Active unless
 * SHEOUT_DOCUMENT_STORAGE_PROVIDER=s3.
 * <p>
 * ASSUMPTION FLAGGED: {@link #resolveUrl} returns a local file:// path, not
 * an HTTP URL - there is no endpoint in this scaffold that serves files
 * back out of the local directory. That's fine for dev (an admin working
 * against docker-compose can open the file directly on disk), but it means
 * the "admin can open the document" part of the review flow only actually
 * works end-to-end against S3. Not built further since a file-serving
 * proxy endpoint wasn't asked for.
 */
@Component
@ConditionalOnProperty(name = "sheout.document-storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalDiskDocumentStorage implements DocumentStorage {

    private final Path root;

    public LocalDiskDocumentStorage(@Value("${sheout.document-storage.local.path:./data/documents}") String path) {
        this.root = Path.of(path);
    }

    @Override
    public String store(UUID accountId, String documentType, DocumentUpload upload) {
        try {
            String key = DocumentKeys.build(accountId, documentType, upload.filename());
            Path target = root.resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, upload.content());
            return key;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public String resolveUrl(String storageKey) {
        return root.resolve(storageKey).toUri().toString();
    }
}
