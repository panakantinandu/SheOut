package com.sheout.staff.internal;

import java.util.Set;

/**
 * The names the staff module writes into the audit log for its own events.
 * Kept as strings, not an enum, so a row written by a newer release still
 * reads in an older one. Console changes elsewhere are recorded as
 * "METHOD /path/{pattern}"; marked reads under their own name.
 */
final class StaffActions {

    static final String SIGN_IN = "staff.signin";
    static final String SIGN_IN_FAILED = "staff.signin.failed";
    static final String SIGN_IN_LOCKED = "staff.signin.locked";
    static final String SIGN_IN_REFUSED = "staff.signin.refused";
    static final String SIGN_OUT = "staff.signout";
    static final String NEW_DEVICE = "staff.device.new";
    static final String STEP_UP = "staff.stepup";
    static final String STEP_UP_FAILED = "staff.stepup.failed";
    static final String SESSION_ENDED = "staff.session.ended";
    static final String INVITE = "staff.invite";
    static final String INVITE_REVOKE = "staff.invite.revoke";
    static final String JOIN = "staff.join";
    static final String DISABLE = "staff.disable";
    static final String ENABLE = "staff.enable";
    static final String ROLE_CHANGE = "staff.role.change";
    static final String SECOND_FACTOR_RESET = "staff.secondfactor.reset";
    static final String SESSIONS_ENDED_BY_OTHER = "staff.sessions.end";
    static final String PASSWORD_CHANGE = "staff.password.change";
    static final String RECOVERY_CODES = "staff.recoverycodes.new";
    static final String PERMISSION_DENIED = "permission.denied";
    static final String WORK_TAKE = "work.take";
    static final String WORK_RELEASE = "work.release";
    static final String AUDIT_VIEW = "audit.view";
    static final String AUDIT_CHAIN_OK = "audit.chain.ok";
    static final String AUDIT_CHAIN_BROKEN = "audit.chain.broken";

    /** Changes to who may do what: every owner hears about each one. */
    static final Set<String> CHANGES_ACCESS = Set.of(INVITE, DISABLE, ENABLE, ROLE_CHANGE, SECOND_FACTOR_RESET);

    private StaffActions() {
    }

    static String describe(String action) {
        return switch (action) {
            case INVITE -> "sent a staff invitation";
            case DISABLE -> "disabled a member of staff";
            case ENABLE -> "re-enabled a member of staff";
            case ROLE_CHANGE -> "changed a member of staff's role";
            case SECOND_FACTOR_RESET -> "reset a member of staff's password and authenticator";
            default -> action;
        };
    }
}
