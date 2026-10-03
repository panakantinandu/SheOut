package com.sheout.staff.internal;

import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.Approvals;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresAnyPermission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.RequiresStepUp;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.sheout.staff.Permission.STAFF_MANAGE_EMPLOYEE;
import static com.sheout.staff.Permission.STAFF_MANAGE_MANAGER;
import static com.sheout.staff.Permission.STAFF_MANAGE_OWNER;

/**
 * The Staff page: who is on the staff, invitations, and acting on a member.
 * <p>
 * The endpoint annotations only say "some staff management permission"; the
 * real rule depends on whose role is involved and is checked inside
 * (StaffManagementService, and require(role.managedBy()) here for invites):
 * a manager can invite and disable agents, only an owner can do either to a
 * manager or an owner, and only an owner can re-enable anyone.
 */
@RestController
@RequestMapping("/api/v1/admin/staff")
@RequiresAnyPermission({STAFF_MANAGE_EMPLOYEE, STAFF_MANAGE_MANAGER, STAFF_MANAGE_OWNER})
class StaffManagementController {

    private final StaffManagementService management;
    private final StaffInviteService invites;
    private final StaffSessionService sessions;
    private final StaffSettings settings;
    private final Approvals approvals;

    StaffManagementController(StaffManagementService management, StaffInviteService invites,
                              StaffSessionService sessions, StaffSettings settings, Approvals approvals) {
        this.settings = settings;
        this.approvals = approvals;
        this.management = management;
        this.invites = invites;
        this.sessions = sessions;
    }

    @GetMapping
    ResponseEntity<StaffViews.StaffList> list() {
        StaffContext.requireSignedIn();
        Instant now = Instant.now();
        List<StaffViews.RoleOption> grantable = Arrays.stream(StaffRole.values())
                .filter(role -> StaffContext.has(role.managedBy()))
                .map(role -> new StaffViews.RoleOption(role, StaffRoleLabels.label(role), role.requiresAccessExpiry()))
                .toList();
        return ResponseEntity.ok(new StaffViews.StaffList(
                management.all().stream().map(m -> StaffViews.Member.of(m, now)).toList(),
                invites.pending().stream().map(StaffViews.Invite::of).toList(),
                grantable,
                management.activeOwners()));
    }

    record InviteRequest(@NotBlank @Email @Size(max = 254) String email,
                         @NotBlank @Size(max = 80) String displayName,
                         @NotNull StaffRole role,
                         Instant accessExpiresAt) {
    }

    @RequiresStepUp
    @PostMapping("/invites")
    ResponseEntity<?> invite(@Valid @RequestBody InviteRequest body) {
        StaffPrincipal me = StaffContext.require(body.role().managedBy());
        // A new manager or owner needs a second owner's yes (Phase 3).
        if (StaffApprovalExecutors.privileged(body.role())) {
            String email = StaffSignInService.normaliseEmail(body.email());
            if (management.byEmail(email).isPresent()) {
                throw StaffAuthController.inviteFailure(StaffInviteService.InviteFailure.of(StaffInviteService.InviteError.EMAIL_ON_STAFF));
            }
            if (approvals.pending(Approvals.Kind.STAFF_PRIVILEGED, email)) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ASKED", "An invitation for this address is already waiting for approval.");
            }
            return ResponseEntity.accepted().body(approvals.submit(new Approvals.Request(Approvals.Kind.STAFF_PRIVILEGED,
                    "Invite " + email + " as " + StaffRoleLabels.label(body.role()),
                    Approvals.payload(new StaffApprovalExecutors.PrivilegedChange("INVITE", email, body.displayName().trim(),
                            body.role(), null, null, me.staffId(), me.displayName())),
                    null, Permission.STAFF_MANAGE_OWNER, "INVITE", email, null)));
        }
        var result = invites.invite(body.email(), body.displayName(), body.role(),
                accessEnd(body.role(), body.accessExpiresAt()), me.staffId(), me.displayName());
        if (result.isFailure()) {
            throw StaffAuthController.inviteFailure(result.error());
        }
        return ResponseEntity.ok(sent(result.value()));
    }

    @PostMapping("/invites/{inviteId}/revoke")
    ResponseEntity<Void> revokeInvite(@PathVariable UUID inviteId) {
        StaffInviteEntity invite = invites.find(inviteId).orElseThrow(() -> ApiException.notFound("No such invitation"));
        StaffContext.require(invite.getRole().managedBy());
        if (!invites.revoke(inviteId)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_NOT_PENDING", "This invitation was already used or withdrawn.");
        }
        return ResponseEntity.noContent().build();
    }

    record DisableRequest(@NotBlank @Size(max = 500) String reason) {
    }

    @RequiresStepUp
    @PostMapping("/{staffId}/disable")
    ResponseEntity<StaffViews.Member> disable(@PathVariable UUID staffId, @Valid @RequestBody DisableRequest body) {
        return answer(management.disable(StaffContext.requireSignedIn(), staffId, body.reason().trim()));
    }

    @RequiresPermission(STAFF_MANAGE_OWNER)
    @RequiresStepUp
    @PostMapping("/{staffId}/enable")
    ResponseEntity<StaffViews.Member> enable(@PathVariable UUID staffId) {
        return answer(management.enable(StaffContext.requireSignedIn(), staffId));
    }

    record RoleRequest(@NotNull StaffRole role, Instant accessExpiresAt) {
    }

    @RequiresStepUp
    @PostMapping("/{staffId}/role")
    ResponseEntity<?> changeRole(@PathVariable UUID staffId, @Valid @RequestBody RoleRequest body) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        StaffMemberEntity target = management.find(staffId).orElseThrow(() -> ApiException.notFound("No such member of staff"));
        StaffContext.require(target.getRole().managedBy());
        StaffContext.require(body.role().managedBy());
        Instant ends = accessEnd(body.role(), body.accessExpiresAt());
        // Into or out of manager or owner: a second owner decides (Phase 3).
        if (StaffApprovalExecutors.privileged(body.role()) || StaffApprovalExecutors.privileged(target.getRole())) {
            if (target.getId().equals(me.staffId())) {
                throw manageFailure(StaffManagementService.ManageError.SELF);
            }
            if (target.getRole() == body.role()) {
                throw manageFailure(StaffManagementService.ManageError.ALREADY);
            }
            if (approvals.pending(Approvals.Kind.STAFF_PRIVILEGED, staffId.toString())) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ASKED", "A role change for this person is already waiting for approval.");
            }
            return ResponseEntity.accepted().body(approvals.submit(new Approvals.Request(Approvals.Kind.STAFF_PRIVILEGED,
                    "Change " + target.getDisplayName() + " from " + StaffRoleLabels.label(target.getRole()) + " to "
                            + StaffRoleLabels.label(body.role()),
                    Approvals.payload(new StaffApprovalExecutors.PrivilegedChange("ROLE", null, null, body.role(), ends,
                            staffId, me.staffId(), me.displayName())),
                    "{\"role\":\"" + target.getRole() + "\"}", Permission.STAFF_MANAGE_OWNER, "STAFF", staffId.toString(), null)));
        }
        return answer(management.changeRole(me, staffId, body.role(), ends));
    }

    /**
     * An auditor's access always ends: on the date given, or 30 days from now
     * (STAFF_AUDITOR_DEFAULT_DAYS) when none is. A date already past is a
     * mistake, not a request for an account that cannot sign in.
     */
    private Instant accessEnd(StaffRole role, Instant given) {
        if (!role.requiresAccessExpiry()) {
            return null;
        }
        if (given == null) {
            return Instant.now().plus(settings.auditorDefaultAccess);
        }
        if (!given.isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPIRY_REQUIRED", "An auditor's access must end on a date in the future.");
        }
        return given;
    }

    @GetMapping("/{staffId}/sessions")
    ResponseEntity<StaffViews.Sessions> sessionsOf(@PathVariable UUID staffId) {
        StaffMemberEntity target = management.find(staffId).orElseThrow(() -> ApiException.notFound("No such member of staff"));
        StaffContext.require(target.getRole().managedBy());
        return ResponseEntity.ok(new StaffViews.Sessions(sessions.live(target, null)));
    }

    @RequiresStepUp
    @PostMapping("/{staffId}/sessions/end-all")
    ResponseEntity<Void> endSessions(@PathVariable UUID staffId) {
        Result<Integer, StaffManagementService.ManageError> result = management.endSessions(StaffContext.requireSignedIn(), staffId);
        if (result.isFailure()) {
            throw manageFailure(result.error());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * A lost phone and lost recovery codes. Owners only (Phase 3 adds a
     * second owner's approval), and never your own - that is what your
     * recovery codes are for.
     */
    @RequiresPermission(STAFF_MANAGE_OWNER)
    @RequiresStepUp
    @PostMapping("/{staffId}/reset-second-factor")
    ResponseEntity<?> resetSecondFactor(@PathVariable UUID staffId) {
        StaffPrincipal me = StaffContext.require(Permission.STAFF_MANAGE_OWNER);
        StaffMemberEntity target = management.find(staffId).orElseThrow(() -> ApiException.notFound("No such member of staff"));
        if (target.getId().equals(me.staffId())) {
            throw manageFailure(StaffManagementService.ManageError.SELF);
        }
        if (target.getStatus() != StaffStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "STAFF_DISABLED", "Re-enable this account first.");
        }
        if (approvals.pending(Approvals.Kind.SECOND_FACTOR_RESET, staffId.toString())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ASKED", "A reset for this person is already waiting for approval.");
        }
        // Someone else's sign-in, wiped and re-issued: a second owner decides (Phase 3).
        return ResponseEntity.accepted().body(approvals.submit(new Approvals.Request(Approvals.Kind.SECOND_FACTOR_RESET,
                "Reset the password and authenticator of " + target.getDisplayName() + " (" + StaffRoleLabels.label(target.getRole()) + ")",
                Approvals.payload(new StaffApprovalExecutors.Reset(staffId)), null, Permission.STAFF_MANAGE_OWNER,
                "STAFF", staffId.toString(), null)));
    }

    private static StaffViews.InviteSent sent(StaffInviteService.Sent sent) {
        return new StaffViews.InviteSent(StaffViews.Invite.of(sent.invite()), sent.emailed(),
                sent.emailed() ? null : sent.link());
    }

    private static ResponseEntity<StaffViews.Member> answer(Result<StaffMemberEntity, StaffManagementService.ManageError> result) {
        if (result.isFailure()) {
            throw manageFailure(result.error());
        }
        return ResponseEntity.ok(StaffViews.Member.of(result.value(), Instant.now()));
    }

    private static ApiException manageFailure(StaffManagementService.ManageError error) {
        return switch (error) {
            case NOT_FOUND -> ApiException.notFound("No such member of staff");
            case SELF -> new ApiException(HttpStatus.CONFLICT, "NOT_ON_YOURSELF",
                    "Someone else has to do this for you.");
            case LAST_OWNER -> new ApiException(HttpStatus.CONFLICT, "LAST_OWNER",
                    "This is the only active owner. Add another owner first.");
            case ALREADY -> new ApiException(HttpStatus.CONFLICT, "NO_CHANGE", "Nothing to change.");
            case EXPIRY_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "EXPIRY_REQUIRED",
                    "An auditor's access must end on a date in the future.");
        };
    }
}
