package com.sheout.marketplace.internal.web;

import com.sheout.sharedkernel.storage.DocumentRules;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Reading a product photo upload - the same rule as a profile photo: the
 * bytes decide what it is, not the name or the type the phone declared, so
 * nothing but a JPEG, PNG or WebP photo is ever stored and shown to others.
 */
final class ProductPhotoUploads {

    private ProductPhotoUploads() {
    }

    static DocumentUpload toUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_REQUIRED", "Choose a photo to upload.");
        }
        if (file.getSize() > DocumentRules.MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_TOO_LARGE", "That photo is too large. Choose one under 10 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Could not read the uploaded photo");
        }
        String actualType = DocumentRules.photoTypeOf(bytes);
        if (actualType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHOTO_TYPE",
                    "That file is not a photo. Choose a JPEG, PNG or WebP photo.");
        }
        return new DocumentUpload(file.getOriginalFilename(), actualType, bytes);
    }
}
