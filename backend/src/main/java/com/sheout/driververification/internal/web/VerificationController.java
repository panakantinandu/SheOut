package com.sheout.driververification.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.driververification.VerificationSummary;
import com.sheout.driververification.internal.VerificationError;
import com.sheout.driververification.internal.VerificationService;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Self-service endpoints - a caller only ever acts on their own record.
 * ASSUMPTION FLAGGED: uploading on someone else's behalf (e.g. an admin
 * submitting a document for a driver over the phone) isn't supported here -
 * not specified, and would need its own authorization rule if added.
 */
@RestController
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping(value = "/api/v1/driver-verification/documents", consumes = "multipart/form-data")
    public ResponseEntity<VerificationSummary> submitDocument(
            @RequestParam("file") MultipartFile file,
            // Optional on the wire, required for partners by the service. A
            // rider has no vehicle and cannot produce one; making it
            // mandatory here would break her submission to enforce a rule
            // that was never about her.
            @RequestParam(value = "rcFile", required = false) MultipartFile rcFile) {
        CurrentAccount caller = requireAuthenticated();
        DocumentUpload upload = toUpload(file);
        DocumentUpload rcUpload = rcFile == null || rcFile.isEmpty() ? null : toUpload(rcFile);
        Result<VerificationSummary, VerificationError> result =
                verificationService.submitDocument(caller.accountId(), upload, rcUpload);
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    @GetMapping("/api/v1/driver-verification/me")
    public ResponseEntity<VerificationSummary> getMyStatus() {
        CurrentAccount caller = requireAuthenticated();
        return verificationService.findByAccountId(caller.accountId())
                .map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("No verification record found yet for this account"));
    }

    static CurrentAccount requireAuthenticated() {
        return CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    static ApiException toApiException(VerificationError error) {
        return switch (error) {
            case RECORD_NOT_FOUND -> ApiException.notFound("No verification record found for this account");
            case STORAGE_FAILED ->
                    new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Failed to store document");
            case NOT_UNDER_REVIEW ->
                    new ApiException(HttpStatus.CONFLICT, "Conflict", "Gender verification is not currently under review");
            case POLICE_VERIFICATION_NOT_APPLICABLE ->
                    new ApiException(HttpStatus.CONFLICT, "Conflict", "Police verification does not apply to this account");
            case INVALID_DECISION ->
                    new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Decision must be VERIFIED or REJECTED");
            case RC_DOCUMENT_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "RC_DOCUMENT_REQUIRED",
                    "Add a photo of your vehicle's registration certificate as well as your ID.");
            case DOCUMENT_TYPE_UNSUPPORTED -> new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_TYPE_UNSUPPORTED",
                    "Send a photo (JPG, PNG, HEIC or WebP) or a PDF. Other kinds of file cannot be opened for review.");
            case DOCUMENT_TOO_SMALL -> new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_TOO_SMALL",
                    "That file is too small to read. Photograph the whole document in good light, or send the original PDF.");
            case DOCUMENT_TOO_LARGE -> new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_TOO_LARGE",
                    "That file is larger than 10 MB. A photo taken with your phone's camera will be well under it.");
        };
    }

    private DocumentUpload toUpload(MultipartFile file) {
        try {
            return new DocumentUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
