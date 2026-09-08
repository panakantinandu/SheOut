package com.sheout.auth.internal.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Verifies a Google OAuth2 access token (from the frontend's
 * google.accounts.oauth2.initTokenClient popup flow - see
 * lib/googleAuth.ts for why this is an access token, not an ID token/JWT)
 * by calling Google's own endpoints, rather than checking a JWT signature
 * locally:
 * <p>
 * 1. tokeninfo - confirms the token was actually issued FOR THIS APP
 * (its `aud` claim must equal our Client ID) and is still valid. Skipping
 * this step would mean trusting ANY valid Google access token, including
 * one issued to a completely different application - the equivalent of
 * GoogleIdTokenVerifier's audience check in the ID-token flow.
 * 2. userinfo - only called once step 1 passes; fetches the verified
 * email/name Google has on file for whoever the token belongs to.
 * <p>
 * Both are plain HTTPS calls to Google, not a library - no
 * signature-verification code to get wrong, and no google-api-client
 * dependency needed.
 * <p>
 * NOT WIRED TO A REAL CLIENT ID ORIGINALLY: sheout.auth.google-client-id
 * is blank by default, opt-in via env var rather than fail-fast at boot
 * (same treatment as Razorpay/Firebase's other unset placeholders) - see
 * isConfigured(). A real Client ID is now set in both environments this
 * app deploys to.
 * <p>
 * PERFORMANCE: tokeninfo and userinfo run in parallel (via virtual
 * threads) rather than one-after-another - they don't actually depend on
 * each other's response (userinfo only needs the raw access token, not
 * tokeninfo's result), so there's no reason to pay both round-trips
 * sequentially. The tokeninfo/audience check still gates whether
 * userinfo's result is ever used, so this doesn't weaken the security
 * check, it just stops waiting on it before starting the other call. Both
 * calls also now have an explicit timeout - previously there was none at
 * all, so a slow/stuck response from Google's servers had no bound on how
 * long this would hang.
 */
@Component
public class GoogleTokenVerifier {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?access_token={token}";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final int TIMEOUT_MS = 5000;

    private final String clientId;
    private final RestClient restClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public GoogleTokenVerifier(@Value("${sheout.auth.google-client-id:}") String clientId) {
        this.clientId = clientId;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return !clientId.isBlank();
    }

    /**
     * Empty if not configured, the token is missing/expired/revoked, was
     * issued for a different app, or Google hasn't itself verified the
     * email on this account (email_verified) - a Google account with an
     * unverified email shouldn't be trusted as proof of that email's
     * identity.
     */
    public Optional<VerifiedGoogleUser> verify(String accessToken) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            CompletableFuture<Map> tokenInfoFuture = CompletableFuture.supplyAsync(
                    () -> restClient.get().uri(TOKENINFO_URL, accessToken).retrieve().body(Map.class), executor);
            CompletableFuture<Map> userInfoFuture = CompletableFuture.supplyAsync(
                    () -> restClient.get().uri(USERINFO_URL).header("Authorization", "Bearer " + accessToken).retrieve().body(Map.class),
                    executor);

            Map<String, Object> tokenInfo = tokenInfoFuture.join();
            if (tokenInfo == null || !clientId.equals(tokenInfo.get("aud"))) {
                return Optional.empty();
            }

            Map<String, Object> userInfo = userInfoFuture.join();
            if (userInfo == null) {
                return Optional.empty();
            }
            Object email = userInfo.get("email");
            Object emailVerified = userInfo.get("email_verified");
            if (!(email instanceof String) || !isTrue(emailVerified)) {
                return Optional.empty();
            }
            Object name = userInfo.get("name");
            return Optional.of(new VerifiedGoogleUser(((String) email).toLowerCase(), name == null ? null : name.toString()));
        } catch (RestClientException | CompletionException ex) {
            // Includes 4xx from either endpoint (expired/invalid/revoked token) and timeouts - not our concern to distinguish further.
            return Optional.empty();
        }
    }

    /** Google's endpoints return email_verified as a real JSON boolean in this response - defensively also accept a string, in case that ever changes. */
    private boolean isTrue(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    public record VerifiedGoogleUser(String email, String name) {
    }
}
