package com.sheout.driververification.internal.provider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The swap point for a background-verification vendor (AuthBridge, IDfy,
 * OnGrid and the like), the same shape as OtpSender and DocumentStorage:
 * the flow depends on this, and the vendor is a configuration choice.
 * <p>
 * The only implementation is ManualVerificationProvider, which does
 * nothing - SheOut's operators record results by hand in the console. No
 * vendor is integrated, and no vendor's API is guessed at here: the request
 * carries only what SheOut knows it has the right to send (her account and
 * the consent she gave), and each vendor's own request and webhook formats
 * belong inside that vendor's implementation, written against its real
 * documentation.
 * <p>
 * A vendor's result never decides anything by itself. It arrives as a
 * BACKGROUND_CHECK_REPORT document for an operator to read, and the police
 * check is still recorded by a person, with POLICE_ACCEPT_THIRD_PARTY_BGV_ALONE
 * deciding whether a report can stand without a police certificate.
 */
public interface VerificationProvider {

    /** Shown in the console and the audit trail. */
    String name();

    /** False means nothing can be sent and the webhook stays closed. */
    boolean configured();

    /**
     * Starts a check. An implementation gathers what its vendor needs (name,
     * date of birth, address) through other modules' public APIs, at the
     * moment of sending, rather than having it copied here.
     */
    Submission submitBackgroundCheck(Request request);

    /** Asks the vendor for a result, for a check whose webhook never came. */
    Optional<Result> fetchResult(String providerReference);

    /**
     * Reads a webhook body the controller has already authenticated by its
     * signature. Empty when the body carries nothing this provider
     * recognises. The format is the vendor's - nothing is assumed here.
     */
    List<Result> parseWebhook(byte[] body);

    record Request(UUID accountId, String consentVersion, Instant consentAt) {
    }

    record Submission(String providerReference) {
    }

    /**
     * What came back. report is the vendor's own report file, filed for an
     * operator to read; summary is one line for the console.
     */
    record Result(String providerReference, byte[] report, String reportContentType, String summary) {
    }
}
