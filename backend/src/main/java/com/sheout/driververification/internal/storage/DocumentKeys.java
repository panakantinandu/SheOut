package com.sheout.driververification.internal.storage;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds storage keys and strips anything from the client-supplied filename
 * that isn't a safe base name - it arrives as raw, untrusted input
 * (originalFilename off a multipart upload), and both implementations
 * would otherwise pass a string like "../../etc/passwd" straight into a
 * file path (LocalDiskDocumentStorage) or an S3 key (less dangerous there,
 * but still not something to trust unsanitized).
 */
final class DocumentKeys {

    private DocumentKeys() {
    }

    static String build(UUID accountId, String documentType, String rawFilename) {
        return "%s/%s-%d-%s".formatted(accountId, documentType, Instant.now().toEpochMilli(), sanitize(rawFilename));
    }

    private static String sanitize(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "document";
        }
        // Strip any directory components (both separators), then keep only
        // a conservative safe character set.
        String baseName = rawFilename.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        String cleaned = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "document" : cleaned;
    }
}
