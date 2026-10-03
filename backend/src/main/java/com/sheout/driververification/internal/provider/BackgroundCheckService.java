package com.sheout.driververification.internal.provider;

import com.sheout.driververification.PartnerDocumentSummary;
import com.sheout.driververification.VerificationConsentStatus;
import com.sheout.driververification.internal.PartnerDocumentService;
import com.sheout.driververification.internal.VerificationConsent;
import com.sheout.driververification.internal.VerificationError;
import com.sheout.driververification.internal.VerificationService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Sending a partner for a background check, and taking the answer in -
 * through whichever VerificationProvider is configured. With the default
 * (manual) both are closed: requestCheck answers PROVIDER_NOT_CONFIGURED and
 * the webhook is a 404.
 */
@Service
public class BackgroundCheckService {

    private final VerificationProvider provider;
    private final PartnerDocumentService documents;
    private final VerificationConsent consent;
    private final VerificationService verification;

    BackgroundCheckService(VerificationProvider provider, PartnerDocumentService documents, VerificationConsent consent,
                           VerificationService verification) {
        this.provider = provider;
        this.documents = documents;
        this.consent = consent;
        this.verification = verification;
    }

    public String providerName() {
        return provider.name();
    }

    public boolean providerConfigured() {
        return provider.configured();
    }

    /** An operator asks the provider to check her. Needs a configured provider and her current consent. */
    public Result<PartnerDocumentSummary, VerificationError> requestCheck(UUID accountId, UUID operatorId) {
        if (!provider.configured()) {
            return Result.failure(VerificationError.PROVIDER_NOT_CONFIGURED);
        }
        if (!consent.isCurrent(accountId)) {
            return Result.failure(VerificationError.CONSENT_REQUIRED);
        }
        VerificationConsentStatus given = verification.consentStatus(accountId);
        VerificationProvider.Submission submission = provider.submitBackgroundCheck(
                new VerificationProvider.Request(accountId, given.acceptedVersion(), given.acceptedAt()));
        return Result.success(documents.openProviderCheck(accountId, submission.providerReference(), operatorId));
    }

    /** An authenticated webhook body: every result in it filed for review. Returns how many matched a check. */
    public int acceptWebhook(byte[] body) {
        int matched = 0;
        List<VerificationProvider.Result> results = provider.parseWebhook(body);
        for (VerificationProvider.Result result : results) {
            DocumentUpload report = result.report() == null ? null
                    : new DocumentUpload("background-check-report", result.reportContentType(), result.report());
            if (documents.recordProviderResult(result.providerReference(), report, result.summary()).isPresent()) {
                matched++;
            }
        }
        return matched;
    }
}
