package com.sheout.staff.internal;

import com.sheout.staff.StaffRole;

/** How a role is written for people: in emails, in the console, next to a name. */
final class StaffRoleLabels {

    private StaffRoleLabels() {
    }

    static String label(StaffRole role) {
        return switch (role) {
            case OWNER -> "Owner";
            case MANAGER -> "Operations manager";
            case VERIFICATION_AGENT -> "Verification agent";
            case SUPPORT_AGENT -> "Support agent";
            case SAFETY_RESPONDER -> "Safety responder";
            case FINANCE -> "Finance";
            case MARKETPLACE_MODERATOR -> "Marketplace moderator";
            case AUDITOR -> "Auditor";
        };
    }
}
