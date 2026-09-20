package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSession;
import com.sheout.auth.SessionRevocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which sign-ins are still live, and the one place they are ended.
 * <p>
 * WHY THIS EXISTS. A signed token used to be trusted for its whole thirty
 * days: blocking an account stopped the next sign-in and did nothing to the
 * phone already holding a token - confirmed against production, where a
 * blocked account kept working. A session row per sign-in is what makes
 * "take that access away now" possible at all, and it is also the only way
 * one device can be signed out when a partner picks up another.
 * <p>
 * ONE DEVICE FOR PARTNERS, MANY FOR RIDERS. A partner account driving on two
 * phones at once is a real problem: two people can hold the same account, and
 * whoever collects the trip is not the account anybody vetted. Riders have no
 * such issue - a phone and a laptop are normal - so the rule is deliberately
 * only applied to DRIVER.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessions;
    /** How stale lastActiveAt may get before a request writes it again. */
    private final Duration touchAfter;

    public SessionService(SessionRepository sessions,
                          @Value("${sheout.auth.session-touch-minutes:5}") long touchMinutes) {
        this.sessions = sessions;
        this.touchAfter = Duration.ofMinutes(touchMinutes);
    }

    /**
     * Records a new sign-in and hands back the session the token will carry.
     * <p>
     * For a DRIVER this ends every other live session on the account first,
     * so the previous phone learns on its next request that she signed in
     * elsewhere instead of failing silently.
     */
    @Transactional
    public UUID open(UUID accountId, AccountRole role, String userAgent) {
        if (role == AccountRole.DRIVER) {
            revokeAll(accountId, SessionRevocation.SIGNED_IN_ELSEWHERE);
        }
        SessionEntity session = new SessionEntity(
                accountId, role, DeviceLabel.from(userAgent), truncate(userAgent));
        sessions.save(session);
        return session.getId();
    }

    /** Ends every live session on an account. Blocking and deletion both land here. */
    @Transactional
    public int revokeAll(UUID accountId, SessionRevocation reason) {
        List<SessionEntity> live = sessions.findByAccountIdAndStatusOrderByLastActiveAtDesc(accountId, SessionStatus.ACTIVE);
        live.forEach(session -> session.revoke(reason));
        sessions.saveAll(live);
        if (!live.isEmpty()) {
            log.info("Ended {} session(s) for account {}: {}", live.size(), accountId, reason);
        }
        return live.size();
    }

    /** Ends one session of the caller's own. Unknown ids and other people's are alike "not found". */
    @Transactional
    public boolean revokeOwn(UUID accountId, UUID sessionId, SessionRevocation reason) {
        return sessions.findByIdAndAccountId(sessionId, accountId)
                .map(session -> {
                    session.revoke(reason);
                    sessions.save(session);
                    return true;
                })
                .orElse(false);
    }

    /**
     * The check every authenticated request makes: is this session still live?
     * <p>
     * One lookup by primary key - the same number of queries the request
     * filter made before this existed. When it is live, lastActiveAt is
     * refreshed at most once every few minutes, so a busy screen polling
     * every four seconds does not write a row every four seconds.
     */
    @Transactional
    public Optional<SessionRevocation> checkLive(UUID sessionId) {
        Optional<SessionEntity> found = sessions.findById(sessionId);
        if (found.isEmpty()) {
            // A token whose session was tidied away, or one from before
            // sessions existed: not live, and nothing to explain.
            return Optional.of(SessionRevocation.SIGNED_OUT);
        }
        SessionEntity session = found.get();
        if (session.getStatus() == SessionStatus.REVOKED) {
            return Optional.of(session.getRevokedReason() == null ? SessionRevocation.SIGNED_OUT : session.getRevokedReason());
        }
        Instant now = Instant.now();
        if (session.getLastActiveAt().isBefore(now.minus(touchAfter))) {
            session.touch(now);
            sessions.save(session);
        }
        return Optional.empty();
    }

    /** Her own live sessions, newest activity first - see the customer app's Devices screen. */
    @Transactional(readOnly = true)
    public List<AccountSession> liveSessions(UUID accountId, UUID currentSessionId) {
        return sessions.findByAccountIdAndStatusOrderByLastActiveAtDesc(accountId, SessionStatus.ACTIVE).stream()
                .map(session -> new AccountSession(
                        session.getId(),
                        session.getDeviceLabel(),
                        session.getCreatedAt(),
                        session.getLastActiveAt(),
                        session.getId().equals(currentSessionId)))
                .toList();
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 400 ? userAgent : userAgent.substring(0, 400);
    }
}
