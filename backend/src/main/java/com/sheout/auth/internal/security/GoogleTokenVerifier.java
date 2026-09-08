package com.sheout.auth.internal.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

/**
 * Verifies a Google Sign-In ID token server-side against Google's own
 * public keys (signature, issuer, audience, expiry) - the frontend hands
 * over the raw token, this never trusts its claims until GoogleIdTokenVerifier
 * has validated it.
 * <p>
 * NOT WIRED TO A REAL CLIENT ID YET: no Google Cloud OAuth Client ID was
 * given for this build - sheout.auth.google-client-id is blank by default,
 * same "opt-in via env var, not fail-fast at boot" treatment as
 * Razorpay/Firebase's other unset placeholders (unlike JWT_SECRET, a
 * missing Google config shouldn't prevent the rest of the app from
 * starting). Set GOOGLE_OAUTH_CLIENT_ID and this starts working with no
 * code changes - see isConfigured().
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${sheout.auth.google-client-id:}") String clientId) {
        this.verifier = clientId.isBlank()
                ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(clientId))
                        .build();
    }

    public boolean isConfigured() {
        return verifier != null;
    }

    /**
     * Empty if the token is missing/expired/wrongly-signed/for-a-different-
     * client, or if Google hasn't itself verified the email on this
     * account (email_verified claim) - a Google account with an unverified
     * email shouldn't be trusted as proof of that email's identity.
     */
    public Optional<VerifiedGoogleUser> verify(String idTokenString) {
        if (verifier == null) {
            return Optional.empty();
        }
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return Optional.empty();
            }
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            Boolean emailVerified = payload.getEmailVerified();
            if (email == null || emailVerified == null || !emailVerified) {
                return Optional.empty();
            }
            Object name = payload.get("name");
            return Optional.of(new VerifiedGoogleUser(email.toLowerCase(), name == null ? null : name.toString()));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public record VerifiedGoogleUser(String email, String name) {
    }
}
