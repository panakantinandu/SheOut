package com.sheout.staff.internal;

import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.ratelimit.TooManyRequestsException;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.ClientAddressResolver;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffPublic;
import com.sheout.staff.StaffRole;
import com.sheout.staff.StaffSignedIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Signing in to the console, signing out, and accepting an invitation.
 * <p>
 * The only console endpoints that work without a session (StaffPublic), so
 * each is rate limited per network address, and signing in also per email -
 * an email nobody on the staff has is limited at the same count as one that
 * is, so the refusal cannot tell them apart. Messages are the same sentence
 * for every wrong answer.
 * <p>
 * No SMS anywhere: SIM swaps and phished SMS codes are how ops consoles are
 * usually taken over. The second factor is an authenticator app (or a
 * single-use recovery code).
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
class StaffAuthController {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String INCORRECT = "Email, password or code is incorrect.";
    private static final String TOO_MANY = "Too many attempts. Wait 15 minutes and try again.";

    private final StaffSignInService signIn;
    private final StaffInviteService invites;
    private final StaffSessionService sessions;
    private final StaffManagementService management;
    private final StaffSettings settings;
    private final RateLimiter rateLimiter;
    private final ClientAddressResolver clientAddress;

    StaffAuthController(StaffSignInService signIn, StaffInviteService invites, StaffSessionService sessions,
                        StaffManagementService management, StaffSettings settings, RateLimiter rateLimiter,
                        ClientAddressResolver clientAddress) {
        this.signIn = signIn;
        this.invites = invites;
        this.sessions = sessions;
        this.management = management;
        this.settings = settings;
        this.rateLimiter = rateLimiter;
        this.clientAddress = clientAddress;
    }

    record SignInRequest(@NotBlank @Size(max = 254) String email,
                         @NotBlank @Size(max = StaffPasswords.MAX_LENGTH) String password,
                         @NotBlank @Size(max = 20) String code) {
    }

    @StaffPublic
    @PostMapping("/login")
    ResponseEntity<StaffViews.SignInResponse> signIn(@Valid @RequestBody SignInRequest body, HttpServletRequest http,
                                                     HttpServletResponse response) {
        String ip = clientAddress.resolve(http);
        String ipKey = "staff-login:ip:" + ip;
        String emailKey = "staff-login:email:" + StaffTokens.sha256(StaffSignInService.normaliseEmail(body.email()));
        rateLimiter.tryConsume(ipKey, settings.loginsPerAddress, WINDOW).orThrow("TOO_MANY_ATTEMPTS", TOO_MANY);
        rateLimiter.tryConsume(emailKey, settings.maxFailedLogins, settings.lockFor).orThrow("TOO_MANY_ATTEMPTS", TOO_MANY);

        Result<StaffSignInService.SignedIn, StaffSignInService.SignInError> result =
                signIn.signIn(body.email(), body.password(), body.code(), http.getHeader("User-Agent"), ip);
        if (result.isFailure()) {
            throw switch (result.error()) {
                case INCORRECT -> new ApiException(HttpStatus.UNAUTHORIZED, "STAFF_SIGN_IN_FAILED", INCORRECT);
                case LOCKED -> new TooManyRequestsException("TOO_MANY_ATTEMPTS", TOO_MANY, settings.lockFor.toSeconds());
                case DISABLED -> new ApiException(HttpStatus.FORBIDDEN, "STAFF_DISABLED",
                        "This staff account has been disabled. Ask an owner if you think this is a mistake.");
                case ACCESS_ENDED -> new ApiException(HttpStatus.FORBIDDEN, "STAFF_ACCESS_ENDED",
                        "Your access to the console has ended. Ask an owner if you still need it.");
                case NETWORK_NOT_ALLOWED -> new ApiException(HttpStatus.FORBIDDEN, "STAFF_NETWORK_NOT_ALLOWED",
                        "Your role can sign in to the console only from approved networks.");
            };
        }
        rateLimiter.release(ipKey);
        rateLimiter.release(emailKey);
        StaffSignInService.SignedIn signedIn = result.value();
        StaffCookies.set(response, signedIn.opened().cookieValue(), settings.cookieSecure);
        return ResponseEntity.ok(new StaffViews.SignInResponse(
                me(signedIn.member(), signedIn.opened().session(), signedIn.recoveryCodesLeft()),
                signedIn.usedRecoveryCode()));
    }

    @StaffSignedIn
    @GetMapping("/me")
    ResponseEntity<StaffViews.Me> me() {
        StaffPrincipal principal = StaffContext.requireSignedIn();
        if (principal.legacy()) {
            return ResponseEntity.ok(StaffViews.Me.legacy(principal));
        }
        StaffMemberEntity member = management.find(principal.staffId()).orElseThrow(StaffContext::signInRequired);
        StaffSessionEntity session = sessions.find(principal.sessionId()).orElseThrow(StaffContext::signInRequired);
        return ResponseEntity.ok(me(member, session, signIn.unusedRecoveryCodes(member)));
    }

    record StepUpRequest(@NotBlank @Size(max = 10) String code) {
    }

    record StepUpResponse(java.time.Instant validUntil) {
    }

    /**
     * Her authenticator code again, so the next five minutes of sensitive
     * actions on this session go through. Wrong codes are limited per person.
     */
    @StaffSignedIn
    @PostMapping("/step-up")
    ResponseEntity<StepUpResponse> stepUp(@Valid @RequestBody StepUpRequest body) {
        StaffPrincipal principal = StaffContext.requireSignedIn();
        if (principal.legacy()) {
            return ResponseEntity.ok(new StepUpResponse(java.time.Instant.now().plus(settings.stepUpWindow)));
        }
        String key = "staff-stepup:" + principal.staffId();
        rateLimiter.tryConsume(key, settings.maxFailedLogins, WINDOW).orThrow("TOO_MANY_ATTEMPTS", TOO_MANY);
        StaffMemberEntity member = management.find(principal.staffId()).orElseThrow(StaffContext::signInRequired);
        if (!signIn.stepUp(member, principal, body.code())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_CODE",
                    "That code is not right. Use the newest code from your authenticator app.");
        }
        rateLimiter.release(key);
        return ResponseEntity.ok(new StepUpResponse(java.time.Instant.now().plus(settings.stepUpWindow)));
    }

    @StaffSignedIn
    @PostMapping("/logout")
    ResponseEntity<Void> signOut(HttpServletResponse response) {
        StaffPrincipal principal = StaffContext.requireSignedIn();
        if (!principal.legacy()) {
            management.find(principal.staffId()).ifPresent(member -> sessions.signOut(member, principal.sessionId()));
        }
        StaffCookies.clear(response, settings.cookieSecure);
        return ResponseEntity.noContent().build();
    }

    // ---- invitations ---------------------------------------------------------

    record TokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    /** What the link is for, so she sees whose account she is setting up before choosing anything. */
    @StaffPublic
    @PostMapping("/invites/lookup")
    ResponseEntity<StaffViews.InviteLookup> lookup(@Valid @RequestBody TokenRequest body, HttpServletRequest http) {
        limitInvites(http);
        StaffInviteEntity invite = invites.usable(body.token()).orElseThrow(StaffAuthController::linkNotValid);
        return ResponseEntity.ok(new StaffViews.InviteLookup(invite.getEmail(), invite.getDisplayName(), invite.getRole(),
                StaffRoleLabels.label(invite.getRole()), invite.isReset(), invite.getExpiresAt()));
    }

    record PasswordRequest(@NotBlank @Size(max = 100) String token,
                           @NotBlank @Size(max = StaffPasswords.MAX_LENGTH) String password) {
    }

    @StaffPublic
    @PostMapping("/invites/password")
    ResponseEntity<StaffInviteService.Enrolment> choosePassword(@Valid @RequestBody PasswordRequest body,
                                                                HttpServletRequest http) {
        limitInvites(http);
        var result = invites.choosePassword(body.token(), body.password());
        if (result.isFailure()) {
            throw inviteFailure(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    record ConfirmRequest(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 10) String code) {
    }

    @StaffPublic
    @PostMapping("/invites/confirm")
    ResponseEntity<StaffViews.JoinedResponse> confirm(@Valid @RequestBody ConfirmRequest body, HttpServletRequest http,
                                                      HttpServletResponse response) {
        limitInvites(http);
        // Wrong authenticator codes against one link: a few, not a brute force.
        rateLimiter.tryConsume("staff-invite-code:" + StaffTokens.sha256(body.token()), 10, WINDOW)
                .orThrow("TOO_MANY_ATTEMPTS", TOO_MANY);
        var result = invites.confirmAuthenticator(body.token(), body.code(), http.getHeader("User-Agent"),
                clientAddress.resolve(http));
        if (result.isFailure()) {
            throw inviteFailure(result.error());
        }
        StaffInviteService.Joined joined = result.value();
        StaffCookies.set(response, joined.opened().cookieValue(), settings.cookieSecure);
        return ResponseEntity.ok(new StaffViews.JoinedResponse(
                me(joined.member(), joined.opened().session(), joined.recoveryCodes().size()), joined.recoveryCodes()));
    }

    /**
     * Generous on purpose: an office onboarding a whole team at once shares
     * one address, and each person makes three or four of these calls. What
     * stops guessing is elsewhere - a link is 256 random bits, and wrong
     * authenticator codes are limited per link.
     */
    private void limitInvites(HttpServletRequest http) {
        rateLimiter.tryConsume("staff-invite:ip:" + clientAddress.resolve(http), 100, WINDOW)
                .orThrow("TOO_MANY_ATTEMPTS", TOO_MANY);
    }

    private StaffViews.Me me(StaffMemberEntity member, StaffSessionEntity session, int recoveryCodesLeft) {
        Long owners = member.getRole() == StaffRole.OWNER ? management.activeOwners() : null;
        return StaffViews.Me.of(member, session, recoveryCodesLeft, owners);
    }

    private static ApiException linkNotValid() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITE_NOT_VALID",
                "This link has been used, has expired or was replaced by a newer one. Ask whoever invited you to send another.");
    }

    static ApiException inviteFailure(StaffInviteService.InviteFailure failure) {
        return switch (failure.error()) {
            case LINK_NOT_VALID -> linkNotValid();
            case EMAIL_ON_STAFF -> new ApiException(HttpStatus.CONFLICT, "EMAIL_ON_STAFF",
                    "Someone on the staff already uses this email address.");
            case WEAK_PASSWORD -> new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", failure.message());
            case PASSWORD_NOT_CHOSEN -> new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_NOT_CHOSEN",
                    "Choose a password first.");
            case WRONG_CODE -> new ApiException(HttpStatus.BAD_REQUEST, "WRONG_CODE",
                    "That code is not right. Check the time on your phone is set automatically, and use the newest code.");
        };
    }
}
