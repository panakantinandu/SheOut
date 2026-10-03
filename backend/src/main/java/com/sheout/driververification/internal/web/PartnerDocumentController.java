package com.sheout.driververification.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.driververification.InsuranceUseType;
import com.sheout.driververification.PartnerDocumentSource;
import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.internal.PartnerDocumentService;
import com.sheout.driververification.internal.VerificationError;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDate;

/**
 * A partner sending one of her documents - licence, RC, insurance, PUC,
 * fitness or police certificate - from her checklist, one at a time.
 * <p>
 * One document per call, unlike the ID submission: each has its own number
 * and dates, its own review and its own expiry, and a partner renewing her
 * PUC should not be asked to send her licence again. Where she stands across
 * all of them is users' GET /api/v1/users/driver/me/readiness, which knows
 * her vehicle.
 * <p>
 * What she reads off the document is required (see PartnerDocumentService):
 * the operator checks her answer against the image rather than transcribing.
 */
@RestController
public class PartnerDocumentController {

    /** A partner renewing a few documents at once, with a retake or two each. */
    private static final int UPLOADS_PER_DAY = 20;

    private final PartnerDocumentService documents;
    private final RateLimiter rateLimiter;

    public PartnerDocumentController(PartnerDocumentService documents, RateLimiter rateLimiter) {
        this.documents = documents;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(value = "/api/v1/driver-verification/partner-documents/{type}", consumes = "multipart/form-data")
    public ResponseEntity<PartnerDocumentSummary> upload(
            @PathVariable PartnerDocumentType type,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "documentNumber", required = false) String documentNumber,
            @RequestParam(value = "issuedOn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedOn,
            @RequestParam(value = "validUntil", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validUntil,
            @RequestParam(value = "insuranceUseType", required = false) InsuranceUseType insuranceUseType) {
        CurrentAccount caller = VerificationController.requireAuthenticated();
        if (caller.role() != AccountRole.DRIVER) {
            throw VerificationController.toApiException(VerificationError.NOT_A_PARTNER);
        }
        rateLimiter.tryConsume("partner-document-upload:" + caller.accountId(), UPLOADS_PER_DAY, Duration.ofDays(1))
                .orThrow("You have uploaded documents many times today. Please try again tomorrow, or contact support.");
        if (documentNumber != null && documentNumber.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENT_NUMBER_TOO_LONG", "That number is too long. Check it against the document.");
        }
        Result<PartnerDocumentSummary, VerificationError> result = documents.submit(caller.accountId(), type,
                file == null || file.isEmpty() ? null : toUpload(file),
                new PartnerDocumentService.DocumentFacts(documentNumber, issuedOn, validUntil, insuranceUseType),
                PartnerDocumentSource.PARTNER_UPLOAD, caller.accountId(), false);
        if (result.isFailure()) {
            throw VerificationController.toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    static DocumentUpload toUpload(MultipartFile file) {
        try {
            return new DocumentUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
