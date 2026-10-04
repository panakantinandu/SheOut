package com.sheout.staff.internal;

import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffSignedIn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * Her passkeys, from My account: list, add, remove.
 * <p>
 * Adding or removing one needs a fresh step-up (her code or an existing
 * passkey): somebody who has only taken over an open session must not be
 * able to add their own key and come back with it later. Every change is in
 * the audit log and alerts the owners and her. The last passkey of a role
 * that requires one cannot be removed - she would be locked out of
 * everything but this page.
 * <p>
 * Under /admin/me, so an owner who has none yet can still reach it.
 */
@RestController
@RequestMapping("/api/v1/admin/me/passkeys")
@StaffSignedIn
class StaffPasskeyController {

    private final StaffPasskeys passkeys;
    private final StaffSessionService sessions;
    private final StaffManagementService management;
    private final StaffAuditLog audit;
    private final RateLimiter rateLimiter;

    StaffPasskeyController(StaffPasskeys passkeys, StaffSessionService sessions, StaffManagementService management,
                           StaffAuditLog audit, RateLimiter rateLimiter) {
        this.passkeys = passkeys;
        this.sessions = sessions;
        this.management = management;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    record Passkeys(List<StaffPasskeys.Passkey> passkeys, boolean required, boolean recommended) {
    }

    @GetMapping
    ResponseEntity<Passkeys> list() {
        StaffMemberEntity member = member();
        return ResponseEntity.ok(new Passkeys(passkeys.list(member.getId()), passkeys.required(member.getRole()),
                passkeys.recommended(member.getRole())));
    }

    @PostMapping("/options")
    ResponseEntity<StaffAuthController.PasskeyOptions> options() {
        StaffPrincipal me = StaffContext.requireSignedIn();
        StaffMemberEntity member = member();
        requireSteppedUp(me);
        limit(me);
        return ResponseEntity.ok(StaffAuthController.PasskeyOptions.of(passkeys.startRegistration(member)));
    }

    record Add(@NotNull UUID challengeId, @NotBlank @Size(max = 20000) String credential, @NotBlank @Size(max = 60) String label) {
    }

    @PostMapping
    ResponseEntity<StaffPasskeys.Passkey> add(@Valid @RequestBody Add body) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        StaffMemberEntity member = member();
        requireSteppedUp(me);
        String label = body.label().trim();
        StaffPasskeys.Passkey added = passkeys.finishRegistration(member, body.challengeId(), body.credential(), label)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "PASSKEY_NOT_ADDED",
                        "That passkey could not be added. Try again, and allow your device to save it for this site."));
        audit.record(new StaffAudit.Entry(StaffActions.PASSKEY_ADD, null, StaffAudit.Result.OK, "STAFF",
                member.getId().toString(), null, label), me);
        return ResponseEntity.ok(added);
    }

    @PostMapping("/{passkeyId}/remove")
    ResponseEntity<Void> remove(@PathVariable UUID passkeyId) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        StaffMemberEntity member = member();
        requireSteppedUp(me);
        List<StaffPasskeys.Passkey> mine = passkeys.list(member.getId());
        StaffPasskeys.Passkey target = mine.stream().filter(p -> p.id().equals(passkeyId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("No such passkey"));
        if (mine.size() == 1 && passkeys.required(member.getRole())) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_PASSKEY",
                    "Your role needs a passkey. Add the new one first, then remove this one.");
        }
        passkeys.remove(member.getId(), passkeyId);
        audit.record(new StaffAudit.Entry(StaffActions.PASSKEY_REMOVE, null, StaffAudit.Result.OK, "STAFF",
                member.getId().toString(), null, target.label()), me);
        return ResponseEntity.noContent().build();
    }

    private void requireSteppedUp(StaffPrincipal me) {
        if (!sessions.steppedUp(me.sessionId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STEP_UP_REQUIRED", "Enter the code from your authenticator app to continue.");
        }
    }

    private StaffMemberEntity member() {
        StaffPrincipal me = StaffContext.requireSignedIn();
        if (me.legacy()) {
            throw new ApiException(HttpStatus.CONFLICT, "LEGACY_SIGN_IN",
                    "This is an old phone sign-in. Sign in with a staff account to add a passkey.");
        }
        return management.find(me.staffId()).orElseThrow(StaffContext::signInRequired);
    }

    private void limit(StaffPrincipal me) {
        rateLimiter.tryConsume("staff-me:passkey:" + me.accountId(), 10, Duration.ofMinutes(15))
                .orThrow("TOO_MANY_ATTEMPTS", "Too many attempts. Wait 15 minutes and try again.");
    }
}
