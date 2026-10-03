package com.sheout.staff.internal;

import com.sheout.notifications.OperatorDeviceApi;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffSignedIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Her own account: where she is signed in, her password, her recovery codes,
 * and this browser's SOS alerts. Nothing here reads anybody else's data, so
 * any signed-in member of staff may use all of it.
 */
@RestController
@RequestMapping("/api/v1/admin/me")
@StaffSignedIn
class StaffAccountController {

    private final StaffManagementService management;
    private final StaffSessionService sessions;
    private final StaffSignInService signIn;
    private final OperatorDeviceApi operatorDevices;
    private final StaffSettings settings;
    private final RateLimiter rateLimiter;

    StaffAccountController(StaffManagementService management, StaffSessionService sessions, StaffSignInService signIn,
                           OperatorDeviceApi operatorDevices, StaffSettings settings, RateLimiter rateLimiter) {
        this.management = management;
        this.sessions = sessions;
        this.signIn = signIn;
        this.operatorDevices = operatorDevices;
        this.settings = settings;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/sessions")
    ResponseEntity<StaffViews.Sessions> mySessions() {
        StaffPrincipal me = StaffContext.requireSignedIn();
        return ResponseEntity.ok(new StaffViews.Sessions(sessions.live(member(me), me.sessionId())));
    }

    @PostMapping("/sessions/{sessionId}/end")
    ResponseEntity<Void> endSession(@PathVariable UUID sessionId, HttpServletResponse response) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        if (!sessions.endOne(member(me), sessionId)) {
            throw ApiException.notFound("No such session");
        }
        if (sessionId.equals(me.sessionId())) {
            StaffCookies.clear(response, settings.cookieSecure);
        }
        return ResponseEntity.noContent().build();
    }

    /** Sign out everywhere, this browser included. */
    @PostMapping("/sessions/end-all")
    ResponseEntity<Void> endAll(HttpServletResponse response) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        sessions.endAll(member(me), com.sheout.auth.SessionRevocation.SIGNED_OUT);
        StaffCookies.clear(response, settings.cookieSecure);
        return ResponseEntity.noContent().build();
    }

    record PasswordChange(@NotBlank @Size(max = StaffPasswords.MAX_LENGTH) String currentPassword,
                          @NotBlank @Size(max = StaffPasswords.MAX_LENGTH) String newPassword) {
    }

    /** Signs out every other browser she has; this one stays. */
    @PostMapping("/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChange body) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        limit(me, "password");
        var result = signIn.changePassword(member(me), me.sessionId(), body.currentPassword(), body.newPassword());
        if (result.isFailure()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, result.error().error().name(), result.error().message());
        }
        return ResponseEntity.noContent().build();
    }

    record CodeRequest(@NotBlank @Size(max = 10) String code) {
    }

    record RecoveryCodes(List<String> codes) {
    }

    /** Ten new codes; the old ones stop working. Needs a current authenticator code. */
    @PostMapping("/recovery-codes")
    ResponseEntity<RecoveryCodes> newRecoveryCodes(@Valid @RequestBody CodeRequest body) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        limit(me, "recovery-codes");
        var result = signIn.regenerateRecoveryCodes(member(me), body.code());
        if (result.isFailure()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, result.error().error().name(), result.error().message());
        }
        return ResponseEntity.ok(new RecoveryCodes(result.value()));
    }

    record DeviceRequest(@NotBlank @Size(max = 500) String token) {
    }

    /** SOS alerts to this browser, tied to this sign-in: signing out stops them. */
    @PostMapping("/push-device")
    ResponseEntity<Void> registerPush(@Valid @RequestBody DeviceRequest body, HttpServletRequest http) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        limit(me, "push");
        operatorDevices.registerOperatorDevice(me.accountId(), body.token().trim(), http.getHeader("User-Agent"));
        if (!me.legacy()) {
            sessions.rememberPushToken(me.sessionId(), body.token().trim());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/push-device/remove")
    ResponseEntity<Void> removePush(@Valid @RequestBody DeviceRequest body) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        operatorDevices.unregisterOperatorDevice(me.accountId(), body.token().trim());
        return ResponseEntity.noContent().build();
    }

    private StaffMemberEntity member(StaffPrincipal me) {
        if (me.legacy()) {
            throw new ApiException(HttpStatus.CONFLICT, "LEGACY_SIGN_IN",
                    "This is an old phone sign-in. Sign in with a staff account to manage it.");
        }
        return management.find(me.staffId()).orElseThrow(StaffContext::signInRequired);
    }

    private void limit(StaffPrincipal me, String what) {
        rateLimiter.tryConsume("staff-me:" + what + ":" + me.accountId(), 10, Duration.ofMinutes(15))
                .orThrow("TOO_MANY_ATTEMPTS", "Too many attempts. Wait 15 minutes and try again.");
    }
}
