package com.sheout.staff.internal;

import com.sheout.auth.AccountSession;
import com.sheout.auth.AuthApi;
import com.sheout.auth.SessionRevocation;
import com.sheout.notifications.OperatorDeviceApi;
import com.sheout.staff.StaffPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Console sign-ins: opening one, checking one on every request, ending them.
 * <p>
 * Each is an account_sessions row (so the existing revocation and listing
 * work for staff exactly as for riders) plus a staff_sessions row holding the
 * cookie's hash, the CSRF token and the two clocks:
 * <ul>
 *   <li>idle - 30 minutes since she last DID something. The console polls
 *       (the SOS count, the live map) and marks those requests as background
 *       (X-Staff-Background); they do not count, or a tab left open on the
 *       live map would never time out, which is the case this exists for.</li>
 *   <li>absolute - 8 hours from signing in, whatever she does. A working
 *       shift, not a week on a shared office machine.</li>
 * </ul>
 * Expiry is checked on each request rather than swept: an expired session is
 * ended the moment anything tries to use it.
 */
@Service
class StaffSessionService {

    private static final Logger log = LoggerFactory.getLogger(StaffSessionService.class);

    private final StaffSessionRepository sessions;
    private final StaffMemberRepository members;
    private final AuthApi authApi;
    private final OperatorDeviceApi operatorDevices;
    private final StaffSettings settings;
    private final StaffIpAllowlist allowlist;

    StaffSessionService(StaffSessionRepository sessions, StaffMemberRepository members, AuthApi authApi,
                        OperatorDeviceApi operatorDevices, StaffSettings settings, StaffIpAllowlist allowlist) {
        this.allowlist = allowlist;
        this.sessions = sessions;
        this.members = members;
        this.authApi = authApi;
        this.operatorDevices = operatorDevices;
        this.settings = settings;
    }

    /** A new sign-in: the cookie value to set (never stored) and the session as the console sees it. */
    record Opened(String cookieValue, StaffSessionEntity session) {
    }

    @Transactional
    Opened open(StaffMemberEntity member, String userAgent, String ipAddress) {
        Instant now = Instant.now();
        UUID sessionId = authApi.openStaffSession(member.getAccountId(), userAgent);
        String token = StaffTokens.newToken();
        Instant absolute = now.plus(settings.absoluteLimitFor(member.getRole()));
        if (member.getAccessExpiresAt() != null && member.getAccessExpiresAt().isBefore(absolute)) {
            absolute = member.getAccessExpiresAt();
        }
        StaffSessionEntity session = sessions.save(new StaffSessionEntity(sessionId, member.getId(),
                StaffTokens.sha256(token), StaffTokens.newToken(), ipAddress, now,
                settings.idleTimeoutFor(member.getRole()), absolute));
        return new Opened(token, session);
    }

    /** Why a cookie did not get her in. Sent to the console so it can say so. */
    enum Ended {
        NOT_SIGNED_IN, IDLE, EXPIRED, SIGNED_OUT, ACCESS_CHANGED, DISABLED, NETWORK
    }

    /** Either who she is, or why not. */
    record Check(StaffPrincipal principal, StaffSessionEntity session, Ended ended) {
        static Check refused(Ended why) {
            return new Check(null, null, why);
        }
    }

    /**
     * The check every console request makes. Ends the session on the way
     * out if it has run out, so the next request needs no clock at all.
     */
    @Transactional
    Check authenticate(String cookieValue, boolean userActivity, String ipAddress) {
        if (cookieValue == null || cookieValue.isBlank() || cookieValue.length() > 100) {
            return Check.refused(Ended.NOT_SIGNED_IN);
        }
        Optional<StaffSessionEntity> found = sessions.findByTokenHash(StaffTokens.sha256(cookieValue));
        if (found.isEmpty()) {
            return Check.refused(Ended.NOT_SIGNED_IN);
        }
        StaffSessionEntity session = found.get();
        StaffMemberEntity member = members.findById(session.getStaffId()).orElse(null);
        if (member == null) {
            return Check.refused(Ended.NOT_SIGNED_IN);
        }
        // Disabled first: her sessions were ended when she was, and "disabled"
        // is the sentence she should read, not "your access changed".
        if (member.getStatus() != StaffStatus.ACTIVE) {
            end(member, session, SessionRevocation.ACCESS_CHANGED);
            return Check.refused(Ended.DISABLED);
        }
        Optional<SessionRevocation> revoked = authApi.checkSession(session.getSessionId());
        if (revoked.isPresent()) {
            return Check.refused(revoked.get() == SessionRevocation.ACCESS_CHANGED ? Ended.ACCESS_CHANGED : Ended.SIGNED_OUT);
        }
        Instant now = Instant.now();
        if (member.accessExpired(now) || !session.getAbsoluteExpiresAt().isAfter(now)) {
            end(member, session, SessionRevocation.SIGNED_OUT);
            return Check.refused(Ended.EXPIRED);
        }
        // Every request, not only signing in: a session opened on the office
        // network cannot be carried anywhere else.
        if (!allowlist.allows(member.getRole(), ipAddress)) {
            end(member, session, SessionRevocation.SIGNED_OUT);
            return Check.refused(Ended.NETWORK);
        }
        if (!session.idleExpiresAt().isAfter(now)) {
            end(member, session, SessionRevocation.SIGNED_OUT);
            return Check.refused(Ended.IDLE);
        }
        if (userActivity && session.recordActivity(now, settings.activityWriteEvery)) {
            sessions.save(session);
        }
        return new Check(principalFor(member, session), session, null);
    }

    static StaffPrincipal principalFor(StaffMemberEntity member, StaffSessionEntity session) {
        return new StaffPrincipal(member.getId(), member.getAccountId(), member.getRole(), session.getSessionId(),
                member.getDisplayName(), false);
    }

    @Transactional(readOnly = true)
    Optional<StaffSessionEntity> find(UUID sessionId) {
        return sessions.findById(sessionId);
    }

    /** Signing out of this browser. */
    @Transactional
    void signOut(StaffMemberEntity member, UUID sessionId) {
        sessions.findById(sessionId).ifPresent(session -> end(member, session, SessionRevocation.SIGNED_OUT));
    }

    /** One of her own sessions, from her Sessions list. False when it is not hers. */
    @Transactional
    boolean endOne(StaffMemberEntity member, UUID sessionId) {
        Optional<StaffSessionEntity> session = sessions.findById(sessionId)
                .filter(s -> s.getStaffId().equals(member.getId()));
        session.ifPresent(s -> end(member, s, SessionRevocation.SIGNED_OUT));
        return session.isPresent();
    }

    /**
     * Every console session she has, at once: "sign out everywhere", being
     * disabled, a role change, a second-factor reset. Effective on the next
     * request each of those browsers makes.
     */
    @Transactional
    int endAll(StaffMemberEntity member, SessionRevocation reason) {
        sessions.findByStaffId(member.getId()).forEach(session -> dropPushToken(member, session));
        int ended = authApi.endAllSessions(member.getAccountId(), reason);
        if (ended > 0) {
            log.info("Ended {} console session(s) for staff {}: {}", ended, member.getId(), reason);
        }
        return ended;
    }

    /** Her live sessions, newest activity first, with this one marked. */
    @Transactional(readOnly = true)
    List<AccountSession> live(StaffMemberEntity member, UUID currentSessionId) {
        return authApi.liveSessions(member.getAccountId(), currentSessionId);
    }

    @Transactional
    void rememberPushToken(UUID sessionId, String token) {
        sessions.findById(sessionId).ifPresent(session -> {
            session.rememberPushToken(token);
            sessions.save(session);
        });
    }

    private void end(StaffMemberEntity member, StaffSessionEntity session, SessionRevocation reason) {
        dropPushToken(member, session);
        authApi.endSession(member.getAccountId(), session.getSessionId(), reason);
    }

    /**
     * A browser that signed out, or was signed out, stops getting SOS
     * alerts: the next person at that desk is not on the staff list.
     */
    private void dropPushToken(StaffMemberEntity member, StaffSessionEntity session) {
        if (session.getPushToken() != null) {
            operatorDevices.unregisterOperatorDevice(member.getAccountId(), session.getPushToken());
            session.rememberPushToken(null);
            sessions.save(session);
        }
    }
}
