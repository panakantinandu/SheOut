package com.sheout.driververification.internal.storage;

/**
 * Storage-agnostic representation of an uploaded file - deliberately not
 * Spring's MultipartFile, so DocumentStorage implementations (and tests)
 * don't need a servlet request to exist.
 */
public record DocumentUpload(String filename, String contentType, byte[] content) {
}
