package com.sheout.staff.internal;

import com.sheout.sharedkernel.Result;
import com.sheout.staff.ApprovalExecutor;
import com.sheout.staff.Approvals;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;

/**
 * The staff changes that need a second owner: inviting or promoting a manager
 * or owner (and demoting one), and resetting someone's sign-in. The request
 * carries what to do; this does it when an owner other than the asker
 * approves, as that owner.
 */
@Configuration
class StaffApprovalExecutors {

    /** Inviting a manager or owner, or moving someone into or out of those roles. */
    record PrivilegedChange(String action, String email, String displayName, StaffRole role, Instant accessExpiresAt,
                            UUID staffId, UUID requestedByStaffId, String requestedByName) {
    }

    record Reset(UUID staffId) {
    }

    static boolean privileged(StaffRole role) {
        return role == StaffRole.OWNER || role == StaffRole.MANAGER;
    }

    @Bean
    ApprovalExecutor privilegedStaffChange(StaffInviteService invites, StaffManagementService management) {
        return new ApprovalExecutor() {
            @Override
            public Approvals.Kind kind() {
                return Approvals.Kind.STAFF_PRIVILEGED;
            }

            @Override
            public Outcome execute(String payload) {
                PrivilegedChange change = Approvals.read(payload, PrivilegedChange.class);
                StaffPrincipal approver = StaffContext.requireSignedIn();
                if ("INVITE".equals(change.action())) {
                    var sent = invites.invite(change.email(), change.displayName(), change.role(), change.accessExpiresAt(),
                            change.requestedByStaffId(), change.requestedByName());
                    if (sent.isFailure()) {
                        return Outcome.failed("Not invited: " + sent.error().error());
                    }
                    return new Outcome(true, sent.value().emailed()
                            ? "Invitation emailed to " + change.email() + "."
                            : "Invited. Email is not set up: pass the link on privately.",
                            sent.value().emailed() ? null : sent.value().link());
                }
                Result<StaffMemberEntity, StaffManagementService.ManageError> changed =
                        management.changeRole(approver, change.staffId(), change.role(), change.accessExpiresAt());
                return changed.isSuccess()
                        ? Outcome.done(changed.value().getDisplayName() + " is now " + StaffRoleLabels.label(change.role()) + ".")
                        : Outcome.failed("Role not changed: " + changed.error());
            }
        };
    }

    @Bean
    ApprovalExecutor secondFactorReset(StaffInviteService invites, StaffManagementService management) {
        return new ApprovalExecutor() {
            @Override
            public Approvals.Kind kind() {
                return Approvals.Kind.SECOND_FACTOR_RESET;
            }

            @Override
            public Outcome execute(String payload) {
                Reset reset = Approvals.read(payload, Reset.class);
                StaffPrincipal approver = StaffContext.requireSignedIn();
                if (reset.staffId().equals(approver.staffId())) {
                    return Outcome.failed("Someone other than you must approve a reset of your own sign-in.");
                }
                StaffMemberEntity target = management.find(reset.staffId()).orElse(null);
                if (target == null || target.getStatus() != StaffStatus.ACTIVE) {
                    return Outcome.failed("That member of staff is not active.");
                }
                var sent = invites.resetSecondFactor(target, approver.staffId(), approver.displayName());
                if (sent.isFailure()) {
                    return Outcome.failed("Not reset.");
                }
                return new Outcome(true, sent.value().emailed()
                        ? "Reset. A link was emailed to " + target.getEmail() + "."
                        : "Reset. Email is not set up: pass the link on privately.",
                        sent.value().emailed() ? null : sent.value().link());
            }
        };
    }
}
