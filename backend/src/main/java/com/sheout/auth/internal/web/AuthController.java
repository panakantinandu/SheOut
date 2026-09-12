package com.sheout.auth.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthenticatedSession;
import com.sheout.auth.internal.AuthError;
import com.sheout.auth.internal.AuthService;
import com.sheout.auth.internal.security.GoogleTokenVerifier;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Combined signup/login over phone + OTP, or over Google Sign-In. There is
 * no separate "signup" endpoint for either - verifying a code (or a Google
 * ID token) for an identity with no account yet creates one with the role
 * given in the request.
 */
@RestController
public class AuthController {

    private final AuthService authService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthController(AuthService authService, GoogleTokenVerifier googleTokenVerifier) {
        this.authService = authService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    @PostMapping("/api/v1/auth/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody RequestOtpRequest request) {
        Result<Void, AuthError> result = authService.requestOtp(request.phoneNumber(), request.role());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/auth/otp/verify")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        Result<AuthenticatedSession, AuthError> result =
                authService.verifyOtp(request.phoneNumber(), request.code(), request.role());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(VerifyOtpResponse.from(result.value()));
    }

    /**
     * Google's equivalent of /otp/verify - same response shape
     * (VerifyOtpResponse) so the rest of the app doesn't need to know which
     * method was used to sign in. accessToken is an OAuth2 access token
     * from the frontend's popup flow (not an ID token/JWT - see
     * lib/googleAuth.ts), verified here against Google's own tokeninfo +
     * userinfo endpoints (see GoogleTokenVerifier) before anything about it
     * is trusted; AuthService only ever sees an already-verified email/name.
     */
    @PostMapping("/api/v1/auth/google/verify")
    public ResponseEntity<VerifyOtpResponse> verifyGoogle(@Valid @RequestBody GoogleVerifyRequest request) {
        if (!googleTokenVerifier.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "Google sign-in is not configured on this server");
        }
        GoogleTokenVerifier.VerifiedGoogleUser verified = googleTokenVerifier.verify(request.accessToken())
                .orElseThrow(() -> ApiException.unauthorized("Invalid or expired Google sign-in token"));

        Result<AuthenticatedSession, AuthError> result =
                authService.verifyGoogleSignIn(verified.email(), verified.name(), request.role());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(VerifyOtpResponse.from(result.value()));
    }

    private ApiException toApiException(AuthError error) {
        return switch (error) {
            case OTP_DELIVERY_FAILED ->
                    new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Failed to deliver OTP code");
            case OTP_TOO_MANY_REQUESTS ->
                    new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                            "Too many codes requested for this number. Please wait a minute and try again.");
            case OTP_NOT_FOUND_OR_EXPIRED ->
                    new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "No OTP requested for this number, or it has expired");
            case OTP_CODE_MISMATCH ->
                    new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Incorrect OTP code");
            case ROLE_MISMATCH ->
                    // Shared between the phone and Google flows (see verifyGoogle) - kept
                    // provider-agnostic rather than saying "phone number" for both.
                    new ApiException(HttpStatus.CONFLICT, "Conflict", "This account is already registered under a different role");
            case ADMIN_SELF_SIGNUP_FORBIDDEN ->
                    // A pure role gate, not tied to a specific resource id, so 403
                    // rather than the 404 used for per-resource authorization.
                    ApiException.forbidden("ADMIN accounts cannot be created via self-service signup");
            case EMAIL_LINKED_TO_PHONE_ACCOUNT ->
                    new ApiException(HttpStatus.CONFLICT, "Conflict", "An account already exists with this email - sign in with your phone number instead");
        };
    }

    public record RequestOtpRequest(
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be a valid E.164 phone number") String phoneNumber,
            @NotNull AccountRole role
    ) {
    }

    public record VerifyOtpRequest(
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must be a valid E.164 phone number") String phoneNumber,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code") String code,
            @NotNull AccountRole role
    ) {
    }

    public record GoogleVerifyRequest(
            @NotBlank String accessToken,
            @NotNull AccountRole role
    ) {
    }

    public record VerifyOtpResponse(
            String accessToken,
            java.util.UUID accountId,
            AccountRole role,
            boolean newAccount
    ) {
        static VerifyOtpResponse from(AuthenticatedSession session) {
            return new VerifyOtpResponse(session.accessToken(), session.accountId(), session.role(), session.newAccount());
        }
    }
}
