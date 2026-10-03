package com.sheout.staff.internal;

import com.sheout.auth.AuthApi;
import com.sheout.auth.SessionRevocation;
import com.sheout.notifications.StaffEmailApi;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.logging.Redact;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Invitations: the only way anybody joins the staff, and how an owner's reset
 * of somebody's password and authenticator is completed.
 * <p>
 * THE ORDER IS THE POINT. The invitee proves she holds the link, chooses a
 * password, then scans a new authenticator secret and types back a code from
 * it. Only that last step creates the staff member and signs her in - so
 * there is never a moment when an account exists with a password and no
 * second factor, and nowhere a default password could live.
 * <p>
 * The link is single-use and lasts 24 hours. Inviting the same address again
 * replaces the earlier link rather than leaving two alive.
 */
@Service
class StaffInviteService {

    private static final Logger log = LoggerFactory.getLogger(StaffInviteService.class);
    static final String ISSUER = "SheOut Console";

    private final StaffInviteRepository invites;
    private final StaffMemberRepository members;
    private final StaffPasswords passwords;
    private final StaffSecrets secrets;
    private final StaffSignInService signIn;
    private final StaffSessionService sessions;
    private final AuthApi authApi;
    private final StaffEmailApi email;
    private final StaffSettings settings;
    private final StaffAuditLog audit;
    private final StaffKnownDevices devices;

    StaffInviteService(StaffInviteRepository invites, StaffMemberRepository members, StaffPasswords passwords,
                       StaffSecrets secrets, StaffSignInService signIn, StaffSessionService sessions,
                       AuthApi authApi, StaffEmailApi email, StaffSettings settings, StaffAuditLog audit,
                       StaffKnownDevices devices) {
        this.audit = audit;
        this.devices = devices;
        this.invites = invites;
        this.members = members;
        this.passwords = passwords;
        this.secrets = secrets;
        this.signIn = signIn;
        this.sessions = sessions;
        this.authApi = authApi;
        this.email = email;
        this.settings = settings;
    }

    enum InviteError {
        /** Unknown, used, revoked or expired - the console says the same for all four. */
        LINK_NOT_VALID,
        EMAIL_ON_STAFF,
        WEAK_PASSWORD,
        PASSWORD_NOT_CHOSEN,
        WRONG_CODE
    }

    record InviteFailure(InviteError error, String message) {
        static InviteFailure of(InviteError error) {
            return new InviteFailure(error, null);
        }
    }

    /**
     * A sent invitation. link is the credential: it is emailed when email is
     * set up, and otherwise handed back once, to the person who sent it, to
     * pass on privately. It is never stored or logged here.
     */
    record Sent(StaffInviteEntity invite, String link, boolean emailed) {
    }

    @Transactional
    Result<Sent, InviteFailure> invite(String rawEmail, String displayName, StaffRole role, Instant accessExpiresAt,
                                       UUID invitedByStaffId, String invitedByName) {
        String address = StaffSignInService.normaliseEmail(rawEmail);
        if (members.findByEmail(address).isPresent()) {
            return Result.failure(InviteFailure.of(InviteError.EMAIL_ON_STAFF));
        }
        invites.findByEmailAndStatus(address, StaffInviteEntity.Status.PENDING).forEach(earlier -> {
            earlier.revoke();
            invites.save(earlier);
        });
        String token = StaffTokens.newToken();
        StaffInviteEntity invite = invites.save(new StaffInviteEntity(StaffTokens.sha256(token), address,
                displayName.trim(), role, role.requiresAccessExpiry() ? accessExpiresAt : null, invitedByStaffId,
                null, null, Instant.now().plus(settings.inviteValidFor)));
        String link = linkFor(token);
        boolean emailed = email.send(address, "You're invited to the SheOut console",
                "Hello " + invite.getDisplayName() + ",\n\n"
                        + invitedByName + " has invited you to the SheOut operations console as "
                        + StaffRoleLabels.label(role) + ".\n\n"
                        + "Open this link within " + settings.inviteValidFor.toHours() + " hours to choose a password "
                        + "and set up an authenticator app on your phone:\n\n" + link + "\n\n"
                        + "The link works once. If you were not expecting this email, ignore it.");
        log.info("Staff invite {} for {} as {} by {} ({})", invite.getId(), Redact.email(address), role,
                invitedByStaffId, emailed ? "emailed" : "email not sent - link returned to the inviter");
        audit.record(new StaffAudit.Entry(StaffActions.INVITE, role.managedBy(), StaffAudit.Result.OK, "INVITE",
                invite.getId().toString(), null, "{\"role\":\"" + role + "\",\"emailed\":" + emailed + "}"));
        return Result.success(new Sent(invite, link, emailed));
    }

    /**
     * The first OWNER: same invitation, sent by nobody, linked to her old
     * phone-login ADMIN account when there is one so her earlier decisions
     * stay hers. See OwnerBootstrap for when this runs.
     */
    @Transactional
    Sent bootstrapOwner(String address, UUID legacyAccountId) {
        invites.findByEmailAndStatus(address, StaffInviteEntity.Status.PENDING).forEach(earlier -> {
            earlier.revoke();
            invites.save(earlier);
        });
        String token = StaffTokens.newToken();
        StaffInviteEntity invite = invites.save(new StaffInviteEntity(StaffTokens.sha256(token), address,
                "Owner", StaffRole.OWNER, null, null, null, legacyAccountId,
                Instant.now().plus(settings.inviteValidFor)));
        String link = linkFor(token);
        boolean emailed = email.send(address, "Set up the first SheOut console owner",
                "This is the first owner account for the SheOut operations console.\n\n"
                        + "Open this link within " + settings.inviteValidFor.toHours() + " hours to choose a password "
                        + "and set up an authenticator app:\n\n" + link + "\n\n"
                        + "The link works once. If you did not deploy SheOut, tell whoever did - somebody set "
                        + "SHEOUT_OWNER_BOOTSTRAP_EMAIL to your address.");
        return new Sent(invite, link, emailed);
    }

    /**
     * An owner reset somebody's password and authenticator (a lost phone and
     * lost recovery codes). Her old ones stop working now, every session
     * ends, and the link lets her choose new ones for the same account - her
     * role, history and account id are unchanged.
     */
    @Transactional
    Result<Sent, InviteFailure> resetSecondFactor(StaffMemberEntity target, UUID byStaffId, String byName) {
        StaffMemberEntity member = members.findByIdForUpdate(target.getId()).orElseThrow();
        invites.findByStaffMemberIdAndStatus(member.getId(), StaffInviteEntity.Status.PENDING).forEach(earlier -> {
            earlier.revoke();
            invites.save(earlier);
        });
        member.clearCredentialsForReset();
        members.save(member);
        sessions.endAll(member, SessionRevocation.ACCESS_CHANGED);
        String token = StaffTokens.newToken();
        StaffInviteEntity invite = invites.save(new StaffInviteEntity(StaffTokens.sha256(token), member.getEmail(),
                member.getDisplayName(), member.getRole(), member.getAccessExpiresAt(), byStaffId, member.getId(),
                null, Instant.now().plus(settings.inviteValidFor)));
        String link = linkFor(token);
        boolean emailed = email.send(member.getEmail(), "Set a new password for the SheOut console",
                "Hello " + member.getDisplayName() + ",\n\n"
                        + byName + " has reset your password and authenticator for the SheOut console. "
                        + "Your old ones no longer work.\n\n"
                        + "Open this link within " + settings.inviteValidFor.toHours() + " hours to choose new ones:\n\n"
                        + link + "\n\nIf you did not ask for this, tell an owner straight away.");
        log.info("Staff second-factor reset for {} by {} ({})", member.getId(), byStaffId,
                emailed ? "emailed" : "email not sent - link returned to the owner");
        audit.record(new StaffAudit.Entry(StaffActions.SECOND_FACTOR_RESET, com.sheout.staff.Permission.STAFF_MANAGE_OWNER,
                StaffAudit.Result.OK, "STAFF", member.getId().toString(), null, null));
        return Result.success(new Sent(invite, link, emailed));
    }

    @Transactional(readOnly = true)
    Optional<StaffInviteEntity> usable(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        return invites.findByTokenHash(StaffTokens.sha256(token)).filter(i -> i.usable(Instant.now()));
    }

    /** What she scans: the secret, as a QR code and as text for typing in by hand. */
    record Enrolment(String secret, String otpauthUri, String qrDataUri) {
    }

    /** Step one: she has the link and has chosen a password. Can be repeated until step two. */
    @Transactional
    Result<Enrolment, InviteFailure> choosePassword(String token, String password) {
        Optional<StaffInviteEntity> found = lockedUsable(token);
        if (found.isEmpty()) {
            return Result.failure(InviteFailure.of(InviteError.LINK_NOT_VALID));
        }
        StaffInviteEntity invite = found.get();
        Optional<String> problem = passwords.problemWith(password, invite.getEmail());
        if (problem.isPresent()) {
            return Result.failure(new InviteFailure(InviteError.WEAK_PASSWORD, problem.get()));
        }
        String secret = Totp.newSecret();
        invite.park(passwords.hash(password), secrets.encrypt(secret));
        invites.save(invite);
        String uri = Totp.otpauthUri(ISSUER, invite.getEmail(), secret);
        return Result.success(new Enrolment(secret, uri, QrCodes.svgDataUri(uri)));
    }

    record Joined(StaffMemberEntity member, List<String> recoveryCodes, StaffSessionService.Opened opened) {
    }

    /**
     * Step two: a code from the authenticator she just set up. Creates the
     * staff member (or gives a reset one her new credentials), uses the
     * link up, and signs her in. Her recovery codes are in the answer, once.
     */
    @Transactional
    Result<Joined, InviteFailure> confirmAuthenticator(String token, String code, String userAgent, String ipAddress) {
        Optional<StaffInviteEntity> found = lockedUsable(token);
        if (found.isEmpty()) {
            return Result.failure(InviteFailure.of(InviteError.LINK_NOT_VALID));
        }
        StaffInviteEntity invite = found.get();
        if (invite.getPendingPasswordHash() == null || invite.getPendingTotpSecret() == null) {
            return Result.failure(InviteFailure.of(InviteError.PASSWORD_NOT_CHOSEN));
        }
        String typed = code == null ? "" : code.replaceAll("\\s", "");
        OptionalLong step = Totp.verify(secrets.decrypt(invite.getPendingTotpSecret()), typed, Instant.now(), null);
        if (step.isEmpty()) {
            return Result.failure(InviteFailure.of(InviteError.WRONG_CODE));
        }

        StaffMemberEntity member;
        if (invite.isReset()) {
            member = members.findByIdForUpdate(invite.getStaffMemberId()).orElseThrow();
            member.replaceCredentials(invite.getPendingPasswordHash(), invite.getPendingTotpSecret(), step.getAsLong());
        } else {
            if (members.findByEmail(invite.getEmail()).isPresent()) {
                return Result.failure(InviteFailure.of(InviteError.EMAIL_ON_STAFF));
            }
            UUID accountId = invite.getLinkAccountId() != null ? invite.getLinkAccountId() : authApi.createStaffAccount();
            member = new StaffMemberEntity(accountId, invite.getEmail(), invite.getDisplayName(), invite.getRole(),
                    invite.getPendingPasswordHash(), invite.getPendingTotpSecret(), step.getAsLong(),
                    invite.getAccessExpiresAt(), invite.getInvitedBy());
        }
        member.recordSignIn(Instant.now(), step.getAsLong());
        member = members.save(member);
        invite.markUsed();
        invites.save(invite);
        List<String> codes = signIn.issueRecoveryCodes(member);
        StaffSessionService.Opened opened = sessions.open(member, userAgent, ipAddress);
        devices.rememberAndCheckNew(member.getId(), userAgent);
        audit.record(new StaffAudit.Entry(StaffActions.JOIN, null, StaffAudit.Result.OK, "STAFF", member.getId().toString(),
                null, invite.isReset() ? "set new credentials after a reset" : "joined as " + member.getRole()),
                StaffSessionService.principalFor(member, opened.session()));
        log.info("Staff {} {} as {}", member.getId(), invite.isReset() ? "set new credentials" : "joined", member.getRole());
        return Result.success(new Joined(member, codes, opened));
    }

    @Transactional
    boolean revoke(UUID inviteId) {
        return invites.findById(inviteId).filter(i -> i.getStatus() == StaffInviteEntity.Status.PENDING).map(i -> {
            i.revoke();
            invites.save(i);
            audit.record(new StaffAudit.Entry(StaffActions.INVITE_REVOKE, i.getRole().managedBy(), StaffAudit.Result.OK,
                    "INVITE", i.getId().toString(), null, null));
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    List<StaffInviteEntity> pending() {
        Instant now = Instant.now();
        return invites.findByStatusOrderByCreatedAtDesc(StaffInviteEntity.Status.PENDING).stream()
                .filter(i -> i.usable(now))
                .toList();
    }

    @Transactional(readOnly = true)
    Optional<StaffInviteEntity> find(UUID inviteId) {
        return invites.findById(inviteId);
    }

    private Optional<StaffInviteEntity> lockedUsable(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        return invites.findByTokenHashForUpdate(StaffTokens.sha256(token)).filter(i -> i.usable(Instant.now()));
    }

    /** The fragment never reaches a server log: browsers do not send it. */
    private String linkFor(String token) {
        return settings.consoleUrl + "#invite=" + token;
    }
}
