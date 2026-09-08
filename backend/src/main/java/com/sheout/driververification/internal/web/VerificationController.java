package com.sheout.driververification.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.driververification.VerificationSummary;
import com.sheout.driververification.internal.VerificationError;
import com.sheout.driververification.internal.VerificationService;
import com.sheout.driververification.internal.storage.DocumentUpload;
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
    public ResponseEntity<VerificationSummary> submitDocument(@RequestParam("file") MultipartFile file) {
        CurrentAccount caller = requireAuthenticated();
        DocumentUpload upload = toUpload(file);
        Result<VerificationSummary, VerificationError> result =
                verificationService.submitDocument(caller.accountId(), upload);
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
