package com.sheout.staff.internal;

import com.sheout.auth.AccountSession;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the staff endpoints send. Built field by field from the entities so a
 * hash, a secret or a token can never leave by being added to an entity.
 */
final class StaffViews {

    private StaffViews() {
    }

    /**
     * Her own sign-in, for the console to shape itself by. permissions is
     * what the console hides and shows by - but hiding is a courtesy, the
     * server checks every request. csrfToken goes back on every change.
     */
    record Me(UUID staffId, String displayName, String email, StaffRole role, String roleLabel,
              List<String> permissions, String csrfToken, long idleTimeoutSeconds, Instant idleExpiresAt,
              Instant absoluteExpiresAt, Instant accessExpiresAt, int recoveryCodesLeft, Long activeOwners,
              boolean legacy) {

        static Me of(StaffMemberEntity member, StaffSessionEntity session, int recoveryCodesLeft, Long activeOwners) {
            return new Me(member.getId(), member.getDisplayName(), member.getEmail(), member.getRole(),
                    StaffRoleLabels.label(member.getRole()), keys(member.getRole()), session.getCsrfToken(),
                    session.idleExpiresAt().getEpochSecond() - session.getLastActivityAt().getEpochSecond(),
                    session.idleExpiresAt(), session.getAbsoluteExpiresAt(), member.getAccessExpiresAt(),
                    recoveryCodesLeft, activeOwners, false);
        }

        /** An old phone-and-code ADMIN token, only ever while ADMIN_PHONE_LOGIN_ENABLED is on. */
        static Me legacy(StaffPrincipal principal) {
            return new Me(null, principal.displayName(), null, principal.role(), "Owner (phone sign-in)",
                    keys(principal.role()), null, 0, null, null, null, 0, null, true);
        }

        private static List<String> keys(StaffRole role) {
            return role.permissions().stream().map(Permission::key).sorted().toList();
        }
    }

    record SignInResponse(Me me, boolean usedRecoveryCode) {
    }

    record InviteLookup(String email, String displayName, StaffRole role, String roleLabel, boolean reset,
                        Instant expiresAt) {
    }

    record JoinedResponse(Me me, List<String> recoveryCodes) {
    }

    record Member(UUID id, String displayName, String email, StaffRole role, String roleLabel, String status,
                  Instant lastLoginAt, Instant lockedUntil, Instant accessExpiresAt, Instant disabledAt,
                  String disabledReason, boolean resetPending, Instant createdAt) {

        static Member of(StaffMemberEntity m, Instant now) {
            return new Member(m.getId(), m.getDisplayName(), m.getEmail(), m.getRole(), StaffRoleLabels.label(m.getRole()),
                    m.getStatus().name(), m.getLastLoginAt(), m.isLocked(now) ? m.getLockedUntil() : null,
                    m.getAccessExpiresAt(), m.getDisabledAt(), m.getDisabledReason(), !m.hasSecondFactor(),
                    m.getCreatedAt());
        }
    }

    record Invite(UUID id, String email, String displayName, StaffRole role, String roleLabel, boolean reset,
                  Instant expiresAt, Instant createdAt) {

        static Invite of(StaffInviteEntity i) {
            return new Invite(i.getId(), i.getEmail(), i.getDisplayName(), i.getRole(), StaffRoleLabels.label(i.getRole()),
                    i.isReset(), i.getExpiresAt(), i.getCreatedAt());
        }
    }

    record RoleOption(StaffRole role, String label, boolean requiresAccessExpiry) {
    }

    record StaffList(List<Member> members, List<Invite> invites, List<RoleOption> rolesYouCanGrant,
                     long activeOwners) {
    }

    /** link is set only when email is not set up: pass it on privately. It works once. */
    record InviteSent(Invite invite, boolean emailed, String link) {
    }

    record Sessions(List<AccountSession> sessions) {
    }
}
