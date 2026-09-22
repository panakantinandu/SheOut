package com.sheout.users.internal.web;

import com.sheout.sharedkernel.storage.DocumentRules;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/** Reading a profile photo upload - shared by the rider and partner endpoints so both accept the same files. */
final class ProfilePhotoUploads {

    /** What a phone camera or gallery produces and every browser can show. */
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private ProfilePhotoUploads() {
    }

    static DocumentUpload toUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_REQUIRED", "Choose a photo to upload.");
        }
        if (file.getContentType() == null || !IMAGE_TYPES.contains(file.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHOTO_TYPE",
                    "Choose a JPEG, PNG or WebP photo.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Could not read the uploaded photo");
        }
        // The bytes decide, not the name or the type the phone declared. Both
        // are whatever the sender says: an HTML page or a program called
        // photo.jpg with type image/jpeg was stored and served as a profile
        // photo, to riders and partners and operators alike.
        String actualType = DocumentRules.photoTypeOf(bytes);
        if (actualType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHOTO_TYPE",
                    "That file is not a photo. Choose a JPEG, PNG or WebP photo.");
        }
        return new DocumentUpload(file.getOriginalFilename(), actualType, bytes);
    }
}
