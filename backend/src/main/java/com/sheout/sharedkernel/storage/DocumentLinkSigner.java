package com.sheout.sharedkernel.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Short-lived links to a stored document, the same idea as an S3 presigned
 * URL: the link itself is the permission, because an {@code <img>} tag
 * cannot send a login token.
 * <p>
 * Whoever is allowed to see a document is decided where the link is handed
 * out - a rider's photo goes only to her partner on a trip, an ID document
 * only to an operator. The link then works for a limited time and only for
 * that one key; a changed key or expiry breaks the signature.
 * <p>
 * Expiries are rounded to the next hour boundary plus one hour, so the same
 * photo gets the same link for up to an hour and the browser can cache it,
 * instead of every screen refresh fetching a fresh copy.
 */
@Component
public class DocumentLinkSigner {

    private static final Duration VALIDITY = Duration.ofHours(1);

    private final byte[] secret;
    private final String baseUrl;

    public DocumentLinkSigner(@Value("${sheout.auth.jwt-secret}") String jwtSecret,
                              @Value("${sheout.document-storage.public-base-url:http://localhost:8080}") String baseUrl) {
        // A separate key derived from the JWT secret: a document signature can
        // never be replayed as a token signature or the other way round.
        this.secret = hmac(jwtSecret.getBytes(StandardCharsets.UTF_8), "sheout-document-links".getBytes(StandardCharsets.UTF_8));
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String linkFor(String storageKey) {
        long hour = VALIDITY.toSeconds();
        long expires = (Instant.now().getEpochSecond() / hour + 2) * hour;
        return baseUrl + "/api/v1/documents?key=" + URLEncoder.encode(storageKey, StandardCharsets.UTF_8)
                + "&expires=" + expires + "&signature=" + sign(storageKey, expires);
    }

    /** True only for an unexpired link this server signed for exactly this key. */
    public boolean isValid(String storageKey, long expires, String signature) {
        if (storageKey == null || signature == null || expires < Instant.now().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(sign(storageKey, expires).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    /** Seconds until this link stops working - what the response may be cached for. */
    public long secondsLeft(long expires) {
        return Math.max(0, expires - Instant.now().getEpochSecond());
    }

    private String sign(String storageKey, long expires) {
        byte[] mac = hmac(secret, (storageKey + "\n" + expires).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
