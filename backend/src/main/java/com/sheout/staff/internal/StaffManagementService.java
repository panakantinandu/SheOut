package com.sheout.staff.internal;

import com.sheout.auth.SessionRevocation;
import com.sheout.notifications.OperatorDeviceApi;
import com.sheout.sharedkernel.Result;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffDirectory;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owners and managers running the staff list: disabling, re-enabling,
 * changing roles, signing people out. Inviting is StaffInviteService.
 * <p>
 * WHO MAY ACT ON WHOM is the target's role, not the endpoint: a manager can
 * disable an agent but not another manager. Each method checks
 * StaffContext.require(role.managedBy()) for every role involved - the
 * endpoint's annotation only says "some staff management permission".
 * <p>
 * Rules that protect the business from a single mistake or a single bad
 * actor, enforced here and not only in the console:
 * <ul>
 *   <li>nobody changes or disables themselves - another person must;</li>
 *   <li>the last active OWNER can be neither disabled nor demoted;</li>
 *   <li>a disabled account comes back only through an OWNER.</li>
 * </ul>
 * Phase 3 puts creating or promoting a MANAGER or OWNER behind a second
 * person's approval.
 */
@Service
class StaffManagementService implements StaffDirectory {

    private static final Logger log = LoggerFactory.getLogger(StaffManagementService.class);

    private final StaffMemberRepository members;
    private final StaffSessionService sessions;
    private final OperatorDeviceApi operatorDevices;
    private final StaffAuditLog audit;

    StaffManagementService(StaffMemberRepository members, StaffSessionService sessions,
                           OperatorDeviceApi operatorDevices, StaffAuditLog audit) {
        this.audit = audit;
        this.members = members;
        this.sessions = sessions;
        this.operatorDevices = operatorDevices;
    }

    enum ManageError {
        NOT_FOUND,
        SELF,
        LAST_OWNER,
        ALREADY,
        EXPIRY_REQUIRED
    }

    @Transactional(readOnly = true)
    List<StaffMemberEntity> all() {
        return members.findAllByOrderByStatusAscDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    Optional<StaffMemberEntity> find(UUID staffId) {
        return members.findById(staffId);
    }

    @Transactional(readOnly = true)
    Optional<StaffMemberEntity> byAccount(UUID accountId) {
        return members.findByAccountId(accountId);
    }

    /**
     * Offboarding. Every session ends at once (each browser's next request is
     * refused), every browser stops receiving SOS alerts, and only an OWNER
     * can undo it.
     */
    @Transactional
    Result<StaffMemberEntity, ManageError> disable(StaffPrincipal by, UUID staffId, String reason) {
        Optional<StaffMemberEntity> found = members.findByIdForUpdate(staffId);
        if (found.isEmpty()) {
            return Result.failure(ManageError.NOT_FOUND);
        }
        StaffMemberEntity target = found.get();
        StaffContext.require(target.getRole().managedBy());
        if (target.getId().equals(by.staffId())) {
            return Result.failure(ManageError.SELF);
        }
        if (target.getStatus() == StaffStatus.DISABLED) {
            return Result.failure(ManageError.ALREADY);
        }
        if (isLastActiveOwner(target)) {
            return Result.failure(ManageError.LAST_OWNER);
        }
        target.disable(by.staffId(), reason);
        members.save(target);
        sessions.endAll(target, SessionRevocation.ACCESS_CHANGED);
        operatorDevices.forgetOperatorDevices(target.getAccountId());
        log.info("Staff {} ({}) disabled by {}", target.getId(), target.getRole(), by.staffId());
        audit.record(new StaffAudit.Entry(StaffActions.DISABLE, target.getRole().managedBy(), StaffAudit.Result.OK,
                "STAFF", target.getId().toString(), reason, "{\"status\":[\"ACTIVE\",\"DISABLED\"]}"));
        return Result.success(target);
    }

    /** Owners only, whatever the role of the person coming back. */
    @Transactional
    Result<StaffMemberEntity, ManageError> enable(StaffPrincipal by, UUID staffId) {
        StaffContext.require(Permission.STAFF_MANAGE_OWNER);
        Optional<StaffMemberEntity> found = members.findByIdForUpdate(staffId);
        if (found.isEmpty()) {
            return Result.failure(ManageError.NOT_FOUND);
        }
        StaffMemberEntity target = found.get();
        if (target.getStatus() == StaffStatus.ACTIVE) {
            return Result.failure(ManageError.ALREADY);
        }
        target.enable();
        members.save(target);
        log.info("Staff {} ({}) re-enabled by {}", target.getId(), target.getRole(), by.staffId());
        audit.record(new StaffAudit.Entry(StaffActions.ENABLE, Permission.STAFF_MANAGE_OWNER, StaffAudit.Result.OK,
                "STAFF", target.getId().toString(), null, "{\"status\":[\"DISABLED\",\"ACTIVE\"]}"));
        return Result.success(target);
    }

    /**
     * A new role takes effect on her next sign-in: every session ends, so no
     * browser keeps the old role's permissions for the rest of the shift.
     */
    @Transactional
    Result<StaffMemberEntity, ManageError> changeRole(StaffPrincipal by, UUID staffId, StaffRole newRole,
                                                      Instant accessExpiresAt) {
        Optional<StaffMemberEntity> found = members.findByIdForUpdate(staffId);
        if (found.isEmpty()) {
            return Result.failure(ManageError.NOT_FOUND);
        }
        StaffMemberEntity target = found.get();
        StaffContext.require(target.getRole().managedBy());
        StaffContext.require(newRole.managedBy());
        if (target.getId().equals(by.staffId())) {
            return Result.failure(ManageError.SELF);
        }
        if (target.getRole() == newRole && !newRole.requiresAccessExpiry()) {
            return Result.failure(ManageError.ALREADY);
        }
        if (newRole.requiresAccessExpiry() && (accessExpiresAt == null || !accessExpiresAt.isAfter(Instant.now()))) {
            return Result.failure(ManageError.EXPIRY_REQUIRED);
        }
        if (target.getRole() == StaffRole.OWNER && newRole != StaffRole.OWNER && isLastActiveOwner(target)) {
            return Result.failure(ManageError.LAST_OWNER);
        }
        StaffRole from = target.getRole();
        target.changeRole(newRole, accessExpiresAt);
        members.save(target);
        sessions.endAll(target, SessionRevocation.ACCESS_CHANGED);
        log.info("Staff {} role {} -> {} by {}", target.getId(), from, newRole, by.staffId());
        audit.record(new StaffAudit.Entry(StaffActions.ROLE_CHANGE, newRole.managedBy(), StaffAudit.Result.OK, "STAFF",
                target.getId().toString(), null, "{\"role\":[\"" + from + "\",\"" + newRole + "\"]}"));
        return Result.success(target);
    }

    /** Signs somebody else out everywhere - a lost laptop, a shift that ended badly. */
    @Transactional
    Result<Integer, ManageError> endSessions(StaffPrincipal by, UUID staffId) {
        Optional<StaffMemberEntity> found = members.findById(staffId);
        if (found.isEmpty()) {
            return Result.failure(ManageError.NOT_FOUND);
        }
        StaffContext.require(found.get().getRole().managedBy());
        int ended = sessions.endAll(found.get(), SessionRevocation.SIGNED_OUT);
        log.info("Staff {} signed out everywhere by {} ({} sessions)", staffId, by.staffId(), ended);
        audit.record(new StaffAudit.Entry(StaffActions.SESSIONS_ENDED_BY_OTHER, found.get().getRole().managedBy(),
                StaffAudit.Result.OK, "STAFF", staffId.toString(), null, "{\"sessions\":" + ended + "}"));
        return Result.success(ended);
    }

    @Transactional(readOnly = true)
    long activeOwners() {
        return members.countByRoleAndStatus(StaffRole.OWNER, StaffStatus.ACTIVE);
    }

    private boolean isLastActiveOwner(StaffMemberEntity target) {
        return target.getRole() == StaffRole.OWNER && target.getStatus() == StaffStatus.ACTIVE && activeOwners() <= 1;
    }

    // ---- StaffDirectory --------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<String> label(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return members.findByAccountId(accountId)
                .map(m -> m.getDisplayName() + " (" + StaffRoleLabels.label(m.getRole()) + ")");
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffMember> activeWith(Permission permission) {
        return members.findByStatusOrderByDisplayNameAsc(StaffStatus.ACTIVE).stream()
                .filter(m -> m.getRole().has(permission))
                .filter(m -> !m.accessExpired(Instant.now()))
                .map(m -> new StaffMember(m.getAccountId(), m.getDisplayName(), m.getRole()))
                .toList();
    }
}
