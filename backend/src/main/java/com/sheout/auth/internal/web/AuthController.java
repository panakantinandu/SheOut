package com.sheout.auth.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthenticatedSession;
import com.sheout.auth.internal.AuthError;
import com.sheout.auth.internal.AuthService;
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
 * Combined signup/login over phone + OTP. There is no separate "signup"
 * endpoint - verifying a code for a phone number that has no account yet
 * creates one with the role given in the request.
 */
@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/v1/auth/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody RequestOtpRequest request) {
        requireSelfServiceRole(request.role());
        Result<Void, AuthError> result = authService.requestOtp(request.phoneNumber(), request.role());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/auth/otp/verify")
    public ResponseEntity<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        requireSelfServiceRole(request.role());
        Result<AuthenticatedSession, AuthError> result =
                authService.verifyOtp(request.phoneNumber(), request.code(), request.role());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(VerifyOtpResponse.from(result.value()));
    }

    /**
     * SECURITY FIX: role was previously a fully client-trusted field with no
     * restriction, meaning any phone number could self-serve an ADMIN token
     * by simply passing role=ADMIN to signup - confirmed live against a
     * running instance, not just a theoretical gap. Self-service signup is
     * for CUSTOMER/DRIVER only; ADMIN accounts must be provisioned
     * out-of-band (see the auth README section) until the admin module
     * exists to do this properly.
     */
    private void requireSelfServiceRole(AccountRole role) {
        if (role == AccountRole.ADMIN) {
            throw ApiException.forbidden("ADMIN accounts cannot be created via self-service signup");
        }
    }

    private ApiException toApiException(AuthError error) {
        return switch (error) {
            case OTP_DELIVERY_FAILED ->
                    new ApiException(HttpStatus.BAD_GATEWAY, "Bad Gateway", "Failed to deliver OTP code");
            case OTP_NOT_FOUND_OR_EXPIRED ->
                    new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "No OTP requested for this number, or it has expired");
            case OTP_CODE_MISMATCH ->
                    new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "Incorrect OTP code");
            case ROLE_MISMATCH ->
                    new ApiException(HttpStatus.CONFLICT, "Conflict", "This phone number is already registered under a different role");
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
