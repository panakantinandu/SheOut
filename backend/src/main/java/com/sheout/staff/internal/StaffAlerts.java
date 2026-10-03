package com.sheout.staff.internal;

import com.sheout.notifications.StaffAlertApi;
import com.sheout.notifications.StaffEmailApi;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tells every owner, at once, about the things an owner must hear about
 * without having to go looking:
 * <ul>
 *   <li>an account locked by failed sign-ins (someone may be guessing);</li>
 *   <li>any change to who holds which role, or who is disabled;</li>
 *   <li>any export of personal or financial data;</li>
 *   <li>a burst of reveals - more than 20 phone numbers or addresses shown to
 *       one person in 10 minutes is someone collecting them;</li>
 *   <li>a sign-in from a browser that member of staff has not used before
 *       (she is told too);</li>
 *   <li>the audit chain failing its check.</li>
 * </ul>
 * Each alert goes by push to owners' registered browsers, by email when email
 * is set up, and into the audit log itself (action "alert.*"), which the Audit
 * screen shows at the top - so an alert is never lost because a browser was
 * closed or there is no mail server.
 * <p>
 * Never throws: an alert that cannot be delivered must not undo, or stop,
 * what it is about.
 */
@Component
class StaffAlerts {

    private static final Logger log = LoggerFactory.getLogger(StaffAlerts.class);
    static final int REVEAL_BURST = 20;
    static final Duration REVEAL_WINDOW = Duration.ofMinutes(10);

    private final StaffAuditLog audit;
    private final StaffAuditWriter writer;
    private final StaffMemberRepository members;
    private final StaffEmailApi email;
    private final StaffAlertApi devices;

    StaffAlerts(StaffAuditLog audit, StaffAuditWriter writer, StaffMemberRepository members, StaffEmailApi email,
                StaffAlertApi devices) {
        this.audit = audit;
        this.writer = writer;
        this.members = members;
        this.email = email;
        this.devices = devices;
    }

    /** Looks at one just-written audit row and raises whatever alert it calls for. */
    void consider(StaffAuditWriter.Row row) {
        try {
            String action = row.action();
            if (action.startsWith("alert.")) {
                return;
            }
            String who = describe(row.staffId());
            if (action.equals(StaffActions.SIGN_IN_LOCKED)) {
                raise("signin.locked", row, "A staff account was locked",
                        who + " was locked after repeated wrong sign-in attempts. If that was not them, someone may be guessing.", null);
            } else if (action.startsWith("staff.") && StaffActions.CHANGES_ACCESS.contains(action) && "OK".equals(row.result())) {
                // An invitation is not a person yet: name the role it grants instead.
                String about = action.equals(StaffActions.INVITE)
                        ? roleIn(row.detail())
                        : row.targetId() == null ? null : describe(parse(row.targetId()));
                raise("staff.change", row, "Staff access changed",
                        who + ": " + StaffActions.describe(action) + (about == null ? "" : " (" + about + ")") + ".", null);
            } else if (action.startsWith("export.") && "OK".equals(row.result())) {
                raise("export", row, "Data exported", who + " exported " + action.substring("export.".length()) + ".", null);
            } else if (action.startsWith("pii.") && "OK".equals(row.result()) && row.staffId() != null) {
                long recent = writer.countSince(row.staffId(), "pii.", Instant.now().minus(REVEAL_WINDOW));
                // Exactly at the crossing, so a long burst is one alert, not twenty.
                if (recent == REVEAL_BURST + 1) {
                    raise("reveal.burst", row, "Many personal details revealed",
                            who + " revealed more than " + REVEAL_BURST + " phone numbers or addresses in "
                                    + REVEAL_WINDOW.toMinutes() + " minutes.", null);
                }
            } else if (action.equals(StaffActions.APPROVAL_REQUEST)) {
                raise("approval.request", row, "An approval is needed",
                        who + " asked for a second person's approval: " + summaryIn(row.detail()) + ".", null);
            } else if (action.equals(StaffActions.APPROVAL_SELF)) {
                raise("approval.self", row, "An owner approved her own request",
                        who + " approved their own request, allowed only while SheOut has one owner: "
                                + Optional.ofNullable(row.detail()).orElse("") + ". Add a second owner.", null);
            } else if (action.equals(StaffActions.NEW_DEVICE)) {
                raise("device.new", row, "Sign-in from a new browser",
                        who + " signed in from a browser not seen before: " + Optional.ofNullable(row.detail()).orElse("unknown") + ".",
                        row.staffId());
            } else if (action.equals(StaffActions.AUDIT_CHAIN_BROKEN)) {
                raise("audit.chain", row, "The audit log failed its check",
                        "Audit rows were changed or deleted: " + Optional.ofNullable(row.detail()).orElse("") + ". Look into it now.", null);
            }
        } catch (RuntimeException e) {
            log.error("Staff alert could not be raised for audit row {}: {}", row.seq(), e.getMessage());
        }
    }

    private void raise(String kind, StaffAuditWriter.Row cause, String title, String body, UUID alsoStaffId) {
        audit.recordQuietly(new StaffAudit.Entry("alert." + kind, null, StaffAudit.Result.OK, "AUDIT_ROW",
                Long.toString(cause.seq()), null, body), null);
        List<StaffMemberEntity> recipients = members.findByStatusOrderByDisplayNameAsc(StaffStatus.ACTIVE).stream()
                .filter(m -> m.getRole() == StaffRole.OWNER || m.getId().equals(alsoStaffId))
                .toList();
        for (StaffMemberEntity recipient : recipients) {
            devices.alertStaff(recipient.getAccountId(), title, body);
            email.send(recipient.getEmail(), "SheOut console: " + title,
                    body + "\n\nOpen the console's Audit page for the details.");
        }
        log.warn("STAFF ALERT {}: {}", kind, body);
    }

    private String describe(UUID staffId) {
        if (staffId == null) {
            return "The system";
        }
        return members.findById(staffId).map(m -> m.getDisplayName() + " (" + StaffRoleLabels.label(m.getRole()) + ")")
                .orElse("A staff member");
    }

    private static String summaryIn(String detail) {
        if (detail == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"summary\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(detail);
        return m.find() ? m.group(1).replace("\\\"", "\"") : detail;
    }

    /** "Operations manager" from an invitation row's {"role":"MANAGER",...}. */
    private static String roleIn(String detail) {
        if (detail == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"role\":\"([A-Z_]+)\"").matcher(detail);
        if (!m.find()) {
            return null;
        }
        try {
            return "as " + StaffRoleLabels.label(StaffRole.valueOf(m.group(1)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
