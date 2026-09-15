package com.sheout.sharedkernel.storage;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentLinkSignerTest {

    private static final String SECRET = "a-test-jwt-secret-that-is-long-enough-for-hmac";
    private static final String KEY = "account-1/profile-photo/abc.jpg";

    private final DocumentLinkSigner signer = new DocumentLinkSigner(SECRET, "https://api.example.test/");

    @Test
    void aFreshLinkIsValidForItsOwnKey() {
        Map<String, String> link = params(signer.linkFor(KEY));

        assertThat(signer.linkFor(KEY)).startsWith("https://api.example.test/api/v1/documents?key=");
        assertThat(link.get("key")).isEqualTo(KEY);
        assertThat(signer.isValid(KEY, Long.parseLong(link.get("expires")), link.get("signature"))).isTrue();
    }

    @Test
    void theSignatureDoesNotCarryOverToAnotherKeyOrExpiry() {
        Map<String, String> link = params(signer.linkFor(KEY));
        long expires = Long.parseLong(link.get("expires"));

        assertThat(signer.isValid("account-2/aadhaar/abc.jpg", expires, link.get("signature"))).isFalse();
        assertThat(signer.isValid(KEY, expires + 3600, link.get("signature"))).isFalse();
        assertThat(signer.isValid(KEY, expires, null)).isFalse();
    }

    @Test
    void aLinkSignedWithAnotherSecretIsRejected() {
        Map<String, String> link = params(new DocumentLinkSigner(SECRET + "-other", "https://api.example.test").linkFor(KEY));

        assertThat(signer.isValid(KEY, Long.parseLong(link.get("expires")), link.get("signature"))).isFalse();
    }

    @Test
    void anExpiredLinkIsRejected() {
        long past = Instant.now().getEpochSecond() - 1;

        assertThat(signer.isValid(KEY, past, "anything")).isFalse();
        assertThat(signer.secondsLeft(past)).isZero();
    }

    @Test
    void linksLastBetweenOneAndTwoHours() {
        long expires = Long.parseLong(params(signer.linkFor(KEY)).get("expires"));

        assertThat(expires % 3600).isZero();
        assertThat(signer.secondsLeft(expires)).isBetween(3600L, 7200L);
    }

    private static Map<String, String> params(String url) {
        Map<String, String> out = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return out;
    }
}
