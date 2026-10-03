package com.sheout.driververification.internal.provider;

import com.sheout.sharedkernel.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Where a verification provider would send its results. A stub: closed
 * (404, as if it did not exist) unless a provider is configured and a
 * webhook secret is set, and even then it only authenticates the body and
 * hands it to the provider, which alone knows its vendor's format.
 * <p>
 * Authentication is an HMAC-SHA256 of the raw body with
 * VERIFICATION_PROVIDER_WEBHOOK_SECRET, hex-encoded, in the header named by
 * VERIFICATION_PROVIDER_SIGNATURE_HEADER, compared in constant time. A
 * vendor that signs differently changes that in its own adapter; nothing
 * here is a guess at a particular vendor's scheme beyond "HMAC of the body",
 * which is what this was asked to check.
 */
@RestController
public class VerificationProviderWebhookController {

    private static final Logger log = LoggerFactory.getLogger(VerificationProviderWebhookController.class);

    private final BackgroundCheckService checks;
    private final byte[] secret;
    private final String signatureHeader;

    public VerificationProviderWebhookController(
            BackgroundCheckService checks,
            @Value("${sheout.verification.provider-webhook-secret:}") String secret,
            @Value("${sheout.verification.provider-signature-header:X-Provider-Signature}") String signatureHeader) {
        this.checks = checks;
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.signatureHeader = signatureHeader;
    }

    @PostMapping("/api/v1/verification/provider/webhook")
    public ResponseEntity<Map<String, Integer>> receive(@RequestBody(required = false) byte[] body,
                                                        @RequestHeader Map<String, String> headers) {
        if (!checks.providerConfigured() || secret.length == 0) {
            throw ApiException.notFound("Not found");
        }
        String signature = headers.entrySet().stream()
                .filter(h -> h.getKey().equalsIgnoreCase(signatureHeader))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        byte[] payload = body == null ? new byte[0] : body;
        if (!validSignature(payload, signature)) {
            log.warn("Verification provider webhook refused: bad or missing signature");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Signature does not match");
        }
        return ResponseEntity.accepted().body(Map.of("matched", checks.acceptWebhook(payload)));
    }

    boolean validSignature(byte[] payload, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        byte[] expected = hmac(payload);
        byte[] given;
        try {
            given = HexFormat.of().parseHex(signature.trim().toLowerCase());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return MessageDigest.isEqual(expected, given);
    }

    private byte[] hmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
