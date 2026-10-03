package com.sheout.staff;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.sheout.staff.Permission.*;

/**
 * The roles staff can hold, and THE ONE PLACE each is mapped to permissions.
 * <p>
 * Fixed in code on purpose. A role editable from the console is a role an
 * attacker with one stolen login can widen; a role in code changes only
 * through a reviewed commit, and PermissionMatrixTest fails until the printed
 * matrix it is compared against is updated in the same commit. That snapshot
 * is the document to show the founder.
 * <p>
 * Roles are bundles; nothing outside this file should ever ask which role
 * somebody holds in order to decide what they may do. The exceptions are
 * about the roles themselves - who may hand out which role (see
 * {@link #managedBy()}) and keeping at least one OWNER.
 */
public enum StaffRole {

    /** Founders. Everything. */
    OWNER(EnumSet.allOf(Permission.class)),

    /**
     * The operations head: every queue and safety case, blocking, ops and
     * finance reports, and employees. Not system settings, not insurance, not
     * other managers or owners, and never moving money alone - approving a
     * payout becomes a second signature once the two-person rule is built.
     */
    MANAGER(EnumSet.of(
            VERIFICATION_REVIEW, VERIFICATION_POLICE_RECORD, DOCUMENTS_VIEW, VERIFICATION_ALL,
            PII_PHONE_REVEAL, PII_ADDRESS_REVEAL, USERS_VIEW, USERS_BLOCK,
            TRIPS_VIEW, TRIPS_LIVE_VIEW, SOS_RESPOND, SUPPORT_WORK, SUPPORT_ALL,
            REFUNDS_ISSUE, PAYOUTS_APPROVE, PAYMENTS_VIEW, INVOICES_VIEW,
            SERVICE_HOURS_MANAGE, CONTENT_MANAGE, ANNOUNCEMENTS_SEND, MARKETPLACE_MODERATE,
            REPORTS_OPS, REPORTS_FINANCE,
            STAFF_MANAGE_EMPLOYEE)),

    /** Onboarding: identity, documents and police evidence. No payments, no riders' trips. */
    VERIFICATION_AGENT(EnumSet.of(
            VERIFICATION_REVIEW, VERIFICATION_POLICE_RECORD, DOCUMENTS_VIEW)),

    /** Customer care: tickets, and goodwill credit up to a small limit. No documents. */
    SUPPORT_AGENT(EnumSet.of(
            SUPPORT_WORK, REFUNDS_ISSUE)),

    /** The 24x7 safety desk: SOS and trip alerts, and calling the people in them. */
    SAFETY_RESPONDER(EnumSet.of(
            SOS_RESPOND, TRIPS_LIVE_VIEW, PII_PHONE_REVEAL)),

    /** Accounts: payments, refunds, payouts, invoices, premiums. No documents. */
    FINANCE(EnumSet.of(
            PAYMENTS_VIEW, INVOICES_VIEW, REFUNDS_ISSUE, PAYOUTS_PREPARE, REPORTS_FINANCE)),

    /** The seller desk: shops and products, nothing else. */
    MARKETPLACE_MODERATOR(EnumSet.of(
            MARKETPLACE_MODERATE)),

    /** An external auditor, read-only and time-limited. */
    AUDITOR(EnumSet.of(
            REPORTS_FINANCE, INVOICES_VIEW, PAYMENTS_VIEW));

    private final Set<Permission> permissions;

    StaffRole(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /**
     * The permission needed to invite, disable, change or sign out somebody
     * holding this role. A manager manages employees; only an owner manages
     * managers and owners.
     */
    public Permission managedBy() {
        return switch (this) {
            case OWNER -> STAFF_MANAGE_OWNER;
            case MANAGER -> STAFF_MANAGE_MANAGER;
            default -> STAFF_MANAGE_EMPLOYEE;
        };
    }

    /** An AUDITOR's access must end on a date; nobody else's does. */
    public boolean requiresAccessExpiry() {
        return this == AUDITOR;
    }
}
