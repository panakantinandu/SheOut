package com.sheout.staff;

import java.util.Set;
import java.util.UUID;

/**
 * The member of staff making this request.
 *
 * @param staffId   her staff record; null only for a legacy token (below)
 * @param accountId the account that stands for her in every other module's
 *                  "who did this" column (blocked_by, reviewed_by, a ticket's
 *                  assignee) - see staff's package-info for why staff have one
 * @param sessionId the sign-in this request came through
 * @param legacy    true only for an old phone-and-code ADMIN token, accepted
 *                  while ADMIN_PHONE_LOGIN_ENABLED is on (local development
 *                  and the switch-over); never in production
 */
public record StaffPrincipal(
        UUID staffId,
        UUID accountId,
        StaffRole role,
        UUID sessionId,
        String displayName,
        boolean legacy
) {

    public Set<Permission> permissions() {
        return role.permissions();
    }

    public boolean has(Permission permission) {
        return role.has(permission);
    }
}
