package com.sheout.staff;

import java.util.Arrays;
import java.util.Optional;

/**
 * Everything a member of staff can be allowed to do in the console, in one
 * list.
 * <p>
 * CODE CHECKS PERMISSIONS, NEVER ROLE NAMES. An endpoint says what it needs
 * ({@link RequiresPermission}) and a role is only a bundle of these
 * ({@link StaffRole}). That way "may a verification agent see payouts?" is
 * answered in one place, the matrix StaffRole builds, rather than by reading
 * twenty controllers - and adding a role later is one line, not a hunt for
 * every {@code role == MANAGER}.
 * <p>
 * The key is what the console, logs and the matrix snapshot show. It is
 * stable: renaming one is a change to the snapshot, and so to review.
 * <p>
 * Several of these are not used by any endpoint yet. They are the catalogue
 * the later phases build on (reveal with a reason, refunds, exports, the audit
 * log), held by roles now so that the matrix the founder reviews is the real
 * one rather than whatever happened to be wired first.
 */
public enum Permission {

    // ---- partner and rider verification ------------------------------------
    /** Review IDs, selfies and partner documents; approve or reject; ask for a re-upload. */
    VERIFICATION_REVIEW("verification.review"),
    /** Record a police verification decision and its evidence, or a background check. */
    VERIFICATION_POLICE_RECORD("verification.police.record"),
    /** Open an identity document, police certificate or vehicle paper. */
    DOCUMENTS_VIEW("documents.view"),
    /** Download documents rather than view them one at a time. */
    DOCUMENTS_DOWNLOAD("documents.download"),

    // ---- personal details ---------------------------------------------------
    /** See a full phone number rather than the masked one. */
    PII_PHONE_REVEAL("pii.phone.reveal"),
    /** See a full address rather than the area. */
    PII_ADDRESS_REVEAL("pii.address.reveal"),
    /** Find riders and partners and open their account pages. */
    USERS_VIEW("users.view"),
    /** Block or unblock a rider or partner, and clear a trust review. */
    USERS_BLOCK("users.block"),

    // ---- trips and safety ---------------------------------------------------
    /** Trip lists and trip pages. */
    TRIPS_VIEW("trips.view"),
    /** Where a trip is right now. */
    TRIPS_LIVE_VIEW("trips.live.view"),
    /** SOS alerts, trip alerts and route reviews: see them and act on them. */
    SOS_RESPOND("sos.respond"),

    // ---- customer care ------------------------------------------------------
    /** Read and answer support tickets. */
    SUPPORT_WORK("support.work"),

    // ---- money --------------------------------------------------------------
    /** Issue a refund or goodwill credit, up to the role's configured limit. */
    REFUNDS_ISSUE("refunds.issue"),
    /** Prepare payout requests for payment. */
    PAYOUTS_PREPARE("payouts.prepare"),
    /** Approve prepared payouts as paid. */
    PAYOUTS_APPROVE("payouts.approve"),
    /** The payments ledger. */
    PAYMENTS_VIEW("payments.view"),
    /** Tax invoices. */
    INVOICES_VIEW("invoices.view"),
    /** Insurance policies, enrolments and the bordereau. */
    INSURANCE_MANAGE("insurance.manage"),
    /** Promotions and partner incentives - money SheOut spends. */
    CAMPAIGNS_MANAGE("campaigns.manage"),

    // ---- running the service ------------------------------------------------
    /** Commission, fares, GST and other system settings. */
    CONFIG_EDIT("config.edit"),
    /** When bookings are taken, and pausing them in an emergency. */
    SERVICE_HOURS_MANAGE("service.hours.manage"),
    /** Help and policy text the apps show. */
    CONTENT_MANAGE("content.manage"),
    /** Announcements pushed to every rider or partner. */
    ANNOUNCEMENTS_SEND("announcements.send"),
    /** Review seller shops and products. */
    MARKETPLACE_MODERATE("marketplace.moderate"),
    /** Operations numbers: queues, funnels, usage, waitlists. */
    REPORTS_OPS("reports.ops"),
    /** Finance numbers: revenue, commission, premiums. */
    REPORTS_FINANCE("reports.finance"),

    // ---- staff --------------------------------------------------------------
    /** Invite, disable and reassign employees (every role below MANAGER). */
    STAFF_MANAGE_EMPLOYEE("staff.manage.employee"),
    /** Invite, disable and change MANAGER accounts. */
    STAFF_MANAGE_MANAGER("staff.manage.manager"),
    /** Invite and change OWNER accounts; re-enable anyone; reset a second factor. */
    STAFF_MANAGE_OWNER("staff.manage.owner"),
    /** The staff audit log. */
    AUDIT_VIEW("audit.view"),
    /** Bulk exports of personal or financial data. */
    DATA_EXPORT("data.export");

    private final String key;

    Permission(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<Permission> fromKey(String key) {
        return Arrays.stream(values()).filter(p -> p.key.equals(key)).findFirst();
    }
}
