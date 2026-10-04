package com.sheout.staff.internal;

import com.sheout.sharedkernel.Result;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Signing in, and the credentials a signed-in member of staff manages
 * herself: her password and her recovery codes.
 * <p>
 * ONE STEP, NOT TWO. Email, password and the authenticator code arrive
 * together and are answered together. A two-step form ("password right, now
 * the code") tells whoever is guessing that the password was right, which is
 * worth knowing for every other site she uses it on. Every wrong answer -
 * unknown email, wrong password, wrong code - is the same refusal, after the
 * same amount of work (StaffPasswords.matchDecoy).
 * <p>
 * LOCKOUT. Five wrong answers lock the account for fifteen minutes, counted
 * on the row under a row lock so parallel guesses cannot share a count. While
 * it is locked the password is not even checked, so a lock cannot be used to
 * learn whether a guess was right. Per-email and per-address rate limits in
 * front of this (StaffAuthController) give an email that is not on the staff
 * the same refusals at the same point.
 */
@Service
class StaffSignInService {

    private static final Logger log = LoggerFactory.getLogger(StaffSignInService.class);
    static final int RECOVERY_CODE_COUNT = 10;

    private final StaffMemberRepository members;
    private final StaffRecoveryCodeRepository recoveryCodes;
    private final StaffPasswords passwords;
    private final StaffSecrets secrets;
    private final StaffSessionService sessions;
    private final StaffSettings settings;
    private final StaffIpAllowlist allowlist;
    private final StaffAuditLog audit;
    private final StaffKnownDevices devices;
    private final StaffPasskeys passkeys;

    StaffSignInService(StaffMemberRepository members, StaffRecoveryCodeRepository recoveryCodes,
                       StaffPasswords passwords, StaffSecrets secrets, StaffSessionService sessions,
                       StaffSettings settings, StaffIpAllowlist allowlist, StaffAuditLog audit,
                       StaffKnownDevices devices, StaffPasskeys passkeys) {
        this.passkeys = passkeys;
        this.allowlist = allowlist;
        this.audit = audit;
        this.devices = devices;
        this.members = members;
        this.recoveryCodes = recoveryCodes;
        this.passwords = passwords;
        this.secrets = secrets;
        this.sessions = sessions;
        this.settings = settings;
    }

    enum SignInError {
        /** Unknown email, wrong password or wrong code - never said which. */
        INCORRECT,
        /** Too many wrong answers lately. */
        LOCKED,
        /** Right answers, but an owner has disabled this account. */
        DISABLED,
        /** Right answers, but this (auditor) account's access has ended. */
        ACCESS_ENDED,
        /** Right answers, from a network this role's allowlist does not include. */
        NETWORK_NOT_ALLOWED,
        /** Right password and code, but her role signs in with its passkey once it has one. */
        USE_PASSKEY
    }

    /** A signed passkey answer: which ceremony it answers, and the browser's credential as JSON. */
    record PasskeyAnswer(java.util.UUID challengeId, String credentialJson) {
        boolean present() {
            return challengeId != null && credentialJson != null && !credentialJson.isBlank();
        }
    }

    record SignedIn(StaffMemberEntity member, StaffSessionService.Opened opened, boolean usedRecoveryCode,
                    int recoveryCodesLeft) {
    }

    @Transactional
    Result<SignedIn, SignInError> signIn(String email, String password, String code, String userAgent, String ipAddress) {
        return signIn(email, password, code, null, userAgent, ipAddress);
    }

    @Transactional
    Result<SignedIn, SignInError> signIn(String email, String password, String code, PasskeyAnswer passkey,
                                         String userAgent, String ipAddress) {
        Optional<StaffMemberEntity> found = members.findByEmailForUpdate(normaliseEmail(email));
        if (found.isEmpty()) {
            passwords.matchDecoy(password);
            return Result.failure(SignInError.INCORRECT);
        }
        StaffMemberEntity member = found.get();
        Instant now = Instant.now();
        if (member.isLocked(now)) {
            passwords.matchDecoy(password);
            return Result.failure(SignInError.LOCKED);
        }

        boolean passwordRight = passwords.matches(password, member.getPasswordHash());
        SecondFactor factor = !passwordRight ? SecondFactor.WRONG
                : passkey != null && passkey.present() ? checkPasskey(member, passkey)
                : checkSecondFactor(member, code, now);
        if (!passwordRight || factor.wrong()) {
            boolean lockedNow = member.recordFailure(now, settings.maxFailedLogins, settings.lockFor);
            members.save(member);
            // Which part was wrong goes in the log for the owners, never in the answer.
            String which = !passwordRight ? "password"
                    : passkey != null && passkey.present() ? "passkey" : "authenticator or recovery code";
            if (lockedNow) {
                log.warn("Staff sign-in locked for {} after {} wrong attempts", member.getId(), settings.maxFailedLogins);
                audit.record(failure(StaffActions.SIGN_IN_LOCKED, member, "wrong " + which + "; locked for "
                        + settings.lockFor.toMinutes() + " minutes"), actor(member));
                return Result.failure(SignInError.LOCKED);
            }
            audit.record(failure(StaffActions.SIGN_IN_FAILED, member, "wrong " + which), actor(member));
            return Result.failure(SignInError.INCORRECT);
        }

        // Every answer was right. Only now is it safe to say why she still
        // cannot come in: she has proved who she is.
        if (member.getStatus() != StaffStatus.ACTIVE) {
            audit.record(failure(StaffActions.SIGN_IN_REFUSED, member, "disabled"), actor(member));
            return Result.failure(SignInError.DISABLED);
        }
        if (member.accessExpired(now)) {
            audit.record(failure(StaffActions.SIGN_IN_REFUSED, member, "access ended"), actor(member));
            return Result.failure(SignInError.ACCESS_ENDED);
        }
        if (!allowlist.allows(member.getRole(), ipAddress)) {
            log.warn("Staff {} ({}) signed in correctly from a network outside the role's allowlist", member.getId(), member.getRole());
            audit.record(failure(StaffActions.SIGN_IN_REFUSED, member, "network not on the role's allowlist"), actor(member));
            return Result.failure(SignInError.NETWORK_NOT_ALLOWED);
        }
        // An owner with a passkey uses it: a code can be phished, a passkey
        // cannot. Recovery codes still work - a lost laptop is not a lockout.
        if (factor.step().isPresent() && passkeys.required(member.getRole()) && passkeys.count(member.getId()) > 0) {
            audit.record(failure(StaffActions.SIGN_IN_REFUSED, member, "authenticator code where a passkey is required"),
                    actor(member));
            return Result.failure(SignInError.USE_PASSKEY);
        }
        if (factor.step().isPresent()) {
            member.recordSignIn(now, factor.step().getAsLong());
        } else if (factor.passkey()) {
            member.recordSignInWithRecoveryCode(now);
        } else {
            member.recordSignInWithRecoveryCode(now);
            log.info("Staff {} signed in with a recovery code", member.getId());
        }
        members.save(member);
        StaffSessionService.Opened opened = sessions.open(member, userAgent, ipAddress);
        StaffPrincipal principal = StaffSessionService.principalFor(member, opened.session());
        audit.record(new StaffAudit.Entry(StaffActions.SIGN_IN, null, StaffAudit.Result.OK, "STAFF", member.getId().toString(),
                null, factor.passkey() ? "with a passkey" : factor.recovery() ? "with a recovery code" : null), principal);
        if (devices.rememberAndCheckNew(member.getId(), userAgent)) {
            audit.record(new StaffAudit.Entry(StaffActions.NEW_DEVICE, null, StaffAudit.Result.OK, "STAFF",
                    member.getId().toString(), null, StaffKnownDevices.describe(userAgent)), principal);
        }
        return Result.success(new SignedIn(member, opened, factor.recovery(), unusedRecoveryCodes(member)));
    }

    /**
     * Step-up: her authenticator code again, on a session already open, before
     * a sensitive action. A recovery code does not count - it is for getting
     * back in, not for vouching that her phone is in her hand.
     */
    @Transactional
    boolean stepUp(StaffMemberEntity member, StaffPrincipal principal, String code) {
        return stepUp(member, principal, code, null);
    }

    /** With her passkey instead of a code, when she gives one. */
    @Transactional
    boolean stepUp(StaffMemberEntity member, StaffPrincipal principal, String code, PasskeyAnswer passkey) {
        StaffMemberEntity locked = members.findByIdForUpdate(member.getId()).orElseThrow();
        if (passkey != null && passkey.present()) {
            boolean ok = passkeys.verify(locked, StaffPasskeys.Purpose.STEP_UP, passkey.challengeId(), passkey.credentialJson());
            audit.record(new StaffAudit.Entry(ok ? StaffActions.STEP_UP : StaffActions.STEP_UP_FAILED, null,
                    ok ? StaffAudit.Result.OK : StaffAudit.Result.FAILED, "STAFF", member.getId().toString(), null, "passkey"),
                    principal);
            if (ok) {
                sessions.markSteppedUp(principal.sessionId());
            }
            return ok;
        }
        String typed = code == null ? "" : code.replaceAll("\\s", "");
        OptionalLong step = locked.hasSecondFactor()
                ? Totp.verify(secrets.decrypt(locked.getTotpSecret()), typed, Instant.now(), locked.getTotpLastStep())
                : OptionalLong.empty();
        if (step.isEmpty()) {
            audit.record(new StaffAudit.Entry(StaffActions.STEP_UP_FAILED, null, StaffAudit.Result.FAILED, "STAFF",
                    member.getId().toString(), null, null), principal);
            return false;
        }
        locked.acceptTotpStep(step.getAsLong());
        members.save(locked);
        sessions.markSteppedUp(principal.sessionId());
        audit.record(new StaffAudit.Entry(StaffActions.STEP_UP, null, StaffAudit.Result.OK, "STAFF",
                member.getId().toString(), null, null), principal);
        return true;
    }

    private static StaffAudit.Entry failure(String action, StaffMemberEntity member, String detail) {
        return new StaffAudit.Entry(action, null, StaffAudit.Result.FAILED, "STAFF", member.getId().toString(), null, detail);
    }

    /** The account the attempt was made against: there is no session yet. */
    private static StaffPrincipal actor(StaffMemberEntity member) {
        return new StaffPrincipal(member.getId(), member.getAccountId(), member.getRole(), null, member.getDisplayName(), false);
    }

    /** The authenticator step matched, a recovery code was spent, a passkey signed, or none of these. */
    private record SecondFactor(OptionalLong step, boolean recovery, boolean passkey) {
        static final SecondFactor WRONG = new SecondFactor(OptionalLong.empty(), false, false);
        static final SecondFactor PASSKEY = new SecondFactor(OptionalLong.empty(), false, true);

        boolean wrong() {
            return step.isEmpty() && !recovery && !passkey;
        }
    }

    private SecondFactor checkPasskey(StaffMemberEntity member, PasskeyAnswer passkey) {
        return passkeys.verify(member, StaffPasskeys.Purpose.SIGN_IN, passkey.challengeId(), passkey.credentialJson())
                ? SecondFactor.PASSKEY : SecondFactor.WRONG;
    }

    private SecondFactor checkSecondFactor(StaffMemberEntity member, String code, Instant now) {
        if (code == null || !member.hasSecondFactor()) {
            return SecondFactor.WRONG;
        }
        String typed = code.replaceAll("\\s", "");
        if (typed.length() == Totp.DIGITS && typed.chars().allMatch(Character::isDigit)) {
            OptionalLong step = Totp.verify(secrets.decrypt(member.getTotpSecret()), typed, now, member.getTotpLastStep());
            return step.isPresent() ? new SecondFactor(step, false, false) : SecondFactor.WRONG;
        }
        return spendRecoveryCode(member, typed) ? new SecondFactor(OptionalLong.empty(), true, false) : SecondFactor.WRONG;
    }

    /** Checks every unused code, so how many she has left does not change how long a wrong one takes. */
    private boolean spendRecoveryCode(StaffMemberEntity member, String typed) {
        String normalised = StaffTokens.normaliseRecoveryCode(typed);
        if (normalised.length() != 10) {
            return false;
        }
        String hash = secrets.mac(normalised);
        StaffRecoveryCodeEntity match = null;
        for (StaffRecoveryCodeEntity candidate : recoveryCodes.findByStaffId(member.getId())) {
            if (!candidate.isUsed() && StaffTokens.same(candidate.getCodeHash(), hash) && match == null) {
                match = candidate;
            }
        }
        if (match == null) {
            return false;
        }
        match.markUsed();
        recoveryCodes.save(match);
        return true;
    }

    /** Ten new codes, replacing any she had. Shown once; only their HMACs are kept. */
    @Transactional
    List<String> issueRecoveryCodes(StaffMemberEntity member) {
        recoveryCodes.deleteByStaffId(member.getId());
        recoveryCodes.flush();
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = StaffTokens.newRecoveryCode();
            codes.add(code);
            recoveryCodes.save(new StaffRecoveryCodeEntity(member.getId(), secrets.mac(StaffTokens.normaliseRecoveryCode(code))));
        }
        return codes;
    }

    @Transactional(readOnly = true)
    int unusedRecoveryCodes(StaffMemberEntity member) {
        return (int) recoveryCodes.findByStaffId(member.getId()).stream().filter(c -> !c.isUsed()).count();
    }

    enum CredentialError { WRONG_PASSWORD, WRONG_CODE, WEAK_PASSWORD }

    record CredentialFailure(CredentialError error, String message) {
    }

    /**
     * A new password, given the current one. Every other console session she
     * has ends: if somebody else had her password, changing it is the moment
     * they should lose it.
     */
    @Transactional
    Result<Void, CredentialFailure> changePassword(StaffMemberEntity member, java.util.UUID currentSessionId,
                                                   String currentPassword, String newPassword) {
        if (!passwords.matches(currentPassword, member.getPasswordHash())) {
            return Result.failure(new CredentialFailure(CredentialError.WRONG_PASSWORD, "Your current password is not right."));
        }
        Optional<String> problem = passwords.problemWith(newPassword, member.getEmail());
        if (problem.isPresent()) {
            return Result.failure(new CredentialFailure(CredentialError.WEAK_PASSWORD, problem.get()));
        }
        if (passwords.matches(newPassword, member.getPasswordHash())) {
            return Result.failure(new CredentialFailure(CredentialError.WEAK_PASSWORD, "That is your current password. Choose a new one."));
        }
        StaffMemberEntity locked = members.findByIdForUpdate(member.getId()).orElseThrow();
        locked.changePassword(passwords.hash(newPassword));
        members.save(locked);
        for (var session : sessions.live(locked, currentSessionId)) {
            if (!session.current()) {
                sessions.endOne(locked, session.id());
            }
        }
        return Result.success(null);
    }

    /** New recovery codes, given a current authenticator code - the paper ones may be what was lost. */
    @Transactional
    Result<List<String>, CredentialFailure> regenerateRecoveryCodes(StaffMemberEntity member, String code) {
        StaffMemberEntity locked = members.findByIdForUpdate(member.getId()).orElseThrow();
        String typed = code == null ? "" : code.replaceAll("\\s", "");
        OptionalLong step = locked.hasSecondFactor()
                ? Totp.verify(secrets.decrypt(locked.getTotpSecret()), typed, Instant.now(), locked.getTotpLastStep())
                : OptionalLong.empty();
        if (step.isEmpty()) {
            return Result.failure(new CredentialFailure(CredentialError.WRONG_CODE,
                    "That code is not right. Use the current code from your authenticator app."));
        }
        locked.acceptTotpStep(step.getAsLong());
        members.save(locked);
        return Result.success(issueRecoveryCodes(locked));
    }

    static String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
