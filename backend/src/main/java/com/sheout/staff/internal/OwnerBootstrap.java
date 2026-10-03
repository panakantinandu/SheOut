package com.sheout.staff.internal;

import com.sheout.auth.AuthApi;
import com.sheout.sharedkernel.logging.Redact;
import com.sheout.staff.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * How the first OWNER comes to exist, replacing the old phone-number
 * AdminBootstrap.
 * <p>
 * With SHEOUT_OWNER_BOOTSTRAP_EMAIL set and NO active OWNER, each start sends
 * that address an OWNER invitation (unless one sent earlier is still waiting).
 * Once an OWNER exists this does nothing at all, whatever the variable says -
 * so a leaked or mistyped variable can never mint a second owner on a live
 * system. Owners after the first are invited from the console.
 * <p>
 * THE OLD ADMIN ACCOUNT. If ADMIN_BOOTSTRAP_PHONE still names the phone-login
 * ADMIN account the founder used before, the invitation is linked to it: her
 * staff record takes over that account id, so blocks, reviews and tickets she
 * handled stay attributed to her. Any other ADMIN account simply stops working
 * once ADMIN_PHONE_LOGIN_ENABLED is off; invite those people by email.
 * <p>
 * WITHOUT EMAIL. Production has no SMTP server yet. The link is then written
 * to the log at INFO - which Sentry does not collect (it takes WARN and
 * above) - with a WARN beside it, without the link, saying where to look.
 * The link works once and for 24 hours, and anyone who can read the service's
 * log already controls its configuration. Set SPRING_MAIL_HOST and MAIL_FROM
 * to have it emailed instead.
 */
@Component
class OwnerBootstrap {

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

    private final StaffMemberRepository members;
    private final StaffInviteService invites;
    private final StaffInviteRepository inviteRows;
    private final AuthApi authApi;
    private final String bootstrapEmail;
    private final String legacyAdminPhone;

    OwnerBootstrap(StaffMemberRepository members, StaffInviteService invites, StaffInviteRepository inviteRows,
                   AuthApi authApi,
                   @Value("${sheout.staff.owner-bootstrap-email:}") String bootstrapEmail,
                   @Value("${sheout.admin.bootstrap-phone:}") String legacyAdminPhone) {
        this.members = members;
        this.invites = invites;
        this.inviteRows = inviteRows;
        this.authApi = authApi;
        this.bootstrapEmail = StaffSignInService.normaliseEmail(bootstrapEmail);
        this.legacyAdminPhone = legacyAdminPhone == null ? "" : legacyAdminPhone.trim();
    }

    @EventListener(ApplicationReadyEvent.class)
    void inviteFirstOwner() {
        if (members.countByRoleAndStatus(StaffRole.OWNER, StaffStatus.ACTIVE) > 0) {
            return;
        }
        if (bootstrapEmail.isBlank()) {
            log.warn("STAFF: no active OWNER exists and SHEOUT_OWNER_BOOTSTRAP_EMAIL is not set - "
                    + "nobody can sign in to the console. Set it to the founder's work email and restart.");
            return;
        }
        if (!bootstrapEmail.contains("@")) {
            log.error("STAFF: SHEOUT_OWNER_BOOTSTRAP_EMAIL is not an email address - no owner invitation sent");
            return;
        }
        if (members.findByEmail(bootstrapEmail).isPresent()) {
            log.error("STAFF: no active OWNER, and {} is already on the staff list (disabled or another role). "
                    + "No invitation sent; fix this in the database or set another address.", Redact.email(bootstrapEmail));
            return;
        }
        Instant now = Instant.now();
        var waiting = inviteRows.findByEmailAndStatus(bootstrapEmail, StaffInviteEntity.Status.PENDING).stream()
                .filter(i -> i.getRole() == StaffRole.OWNER && i.usable(now))
                .findFirst();
        if (waiting.isPresent()) {
            log.warn("STAFF: the OWNER invitation for {} is still waiting (expires {}). Not sending another.",
                    Redact.email(bootstrapEmail), waiting.get().getExpiresAt());
            return;
        }
        UUID legacy = legacyAdminPhone.isBlank() ? null : authApi.findAdminAccountByPhone(legacyAdminPhone).orElse(null);
        StaffInviteService.Sent sent = invites.bootstrapOwner(bootstrapEmail, legacy);
        if (sent.emailed()) {
            log.warn("STAFF: no OWNER exists - an OWNER invitation was emailed to {} (expires {}){}",
                    Redact.email(bootstrapEmail), sent.invite().getExpiresAt(),
                    legacy == null ? "" : "; it takes over the phone-login ADMIN account for ADMIN_BOOTSTRAP_PHONE");
        } else {
            log.info("STAFF OWNER INVITATION for {} - open this once, within 24 hours: {}",
                    Redact.email(bootstrapEmail), sent.link());
            log.warn("STAFF: no OWNER exists and email is not set up - the OWNER invitation link is in the INFO "
                    + "line just above this one in the service log (expires {})", sent.invite().getExpiresAt());
        }
    }
}
