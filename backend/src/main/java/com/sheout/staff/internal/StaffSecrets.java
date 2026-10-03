package com.sheout.staff.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The one key staff credentials are protected with (STAFF_SECRETS_KEY), and
 * the two things done with it.
 * <ul>
 *   <li>Authenticator secrets are encrypted (AES-256-GCM). They have to be
 *       read back to check a code, so they cannot be hashed - but a copy of
 *       the database alone must not be enough to produce a code.</li>
 *   <li>Recovery codes are HMAC'd. They are random and long, so a keyed hash
 *       is enough, and unlike a password hash it is cheap to check all ten of
 *       a person's codes on every attempt.</li>
 * </ul>
 * Two sub-keys are derived from the configured one, so the encryption key and
 * the MAC key are never the same bytes.
 * <p>
 * Required, with no default outside the local profile: a server that started
 * without it would either refuse every staff sign-in or, worse, have been
 * given a made-up key whose secrets nobody can read after a restart.
 */
@Component
class StaffSecrets {

    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;
    private static final String VERSION = "v1:";

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec macKey;

    StaffSecrets(@Value("${sheout.staff.secrets-key}") String configuredKey) {
        byte[] master;
        try {
            master = Base64.getDecoder().decode(configuredKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("STAFF_SECRETS_KEY must be base64 (generate one with: openssl rand -base64 32)");
        }
        if (master.length < 32) {
            throw new IllegalStateException("STAFF_SECRETS_KEY must be at least 32 bytes once decoded (openssl rand -base64 32)");
        }
        this.encryptionKey = new SecretKeySpec(derive(master, "staff-totp-encryption"), "AES");
        this.macKey = new SecretKeySpec(derive(master, "staff-recovery-code-mac"), "HmacSHA256");
    }

    String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + sealed.length)
                    .put(nonce).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt a staff secret", e);
        }
    }

    String decrypt(String stored) {
        if (stored == null || !stored.startsWith(VERSION)) {
            throw new IllegalStateException("Not a staff secret this server can read");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, all, 0, NONCE_BYTES));
            return new String(cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key (it was rotated without re-enrolling) or a damaged row.
            throw new IllegalStateException("A staff secret could not be decrypted - was STAFF_SECRETS_KEY changed?", e);
        }
    }

    /** Hex HMAC-SHA256, for recovery codes. */
    String mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] derive(byte[] master, String label) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(master, "HmacSHA256"));
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
