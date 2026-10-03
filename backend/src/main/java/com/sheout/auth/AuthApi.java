package com.sheout.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Auth's public API. Deliberately small: OTP request/verify are only ever
 * called over HTTP by the client apps (there's no cross-module Java caller
 * for them, so they stay internal), but other modules do need to resolve
 * an account by id - e.g. driver-verification's admin queue showing a
 * phone number next to each pending review.
 */
public interface AuthApi {

    Optional<AccountSummary> findAccount(UUID accountId);

    /**
     * Records that this account accepted the Terms and Privacy Policy now.
     * Idempotent - the first acceptance stands, so a later profile edit does
     * not rewrite the date she actually agreed.
     */
    void acceptTerms(UUID accountId);

    /** Whether there is a consent record for this account at all. */
    boolean hasAcceptedTerms(UUID accountId);

    /**
     * When she last used SheOut on any device she is still signed in on - the
     * newest activity across her live sessions. Empty when she has none
     * (signed out everywhere). For telling "hasn't opened the app in a
     * while" from "opened it on another phone yesterday".
     */
    Optional<Instant> lastActiveAt(UUID accountId);

    /**
     * Email addresses of accounts in a role, a page at a time, for an
     * announcement.
     * <p>
     * An account carries an email when she signed in with Google, which is a
     * different place from the address she may have typed into her profile -
     * and for most people it is the only one on file. A broadcast that reads
     * only profiles silently misses them. Blocked accounts are left out.
     * <p>
     * Addresses only: a broadcast has no business reading names or anything
     * else about the people it goes to.
     */
    List<String> findEmailAddresses(AccountRole role, int page, int size);

    /**
     * Whether two accounts belong to the same person - the same phone number
     * or the same Google email. One person can hold a rider account and a
     * partner account (one per app), and must never be matched with herself.
     */
    boolean samePerson(UUID accountA, UUID accountB);

    /**
     * A new ADMIN account to stand for a member of staff in other modules'
     * "who did this" columns. It has no phone number and no email, so nobody
     * can sign in to an app with it; staff credentials live in the staff
     * module, which is the only caller. Signup still refuses role=ADMIN (see
     * AuthController's requireSelfServiceRole).
     */
    UUID createStaffAccount();

    /**
     * The ADMIN account that signs in with this phone number, if there is
     * one: the first OWNER taking over the account she used before staff
     * sign-in existed, so her earlier decisions stay hers.
     */
    Optional<UUID> findAdminAccountByPhone(String phoneNumber);

    /**
     * Opens a session for a staff account's console sign-in. It is an
     * ordinary session row, so it is listed, checked and ended exactly like
     * a rider's - blocking or a revocation takes effect on the next request.
     */
    UUID openStaffSession(UUID accountId, String userAgent);

    /** Empty when the session is live; otherwise why it ended. */
    Optional<SessionRevocation> checkSession(UUID sessionId);

    /** Ends one of this account's sessions. False when it is not one of hers. */
    boolean endSession(UUID accountId, UUID sessionId, SessionRevocation reason);

    /** Ends every live session on the account; returns how many. */
    int endAllSessions(UUID accountId, SessionRevocation reason);

    /** The account's live sessions, newest activity first. */
    List<AccountSession> liveSessions(UUID accountId, UUID currentSessionId);

    /**
     * A page of accounts, searchable by phone or email and narrowable by
     * blocked state. blocked is three-state: null for every account, TRUE
     * for only blocked, FALSE for only active.
     *
     * roles is an include-list and must be non-empty, the same shape
     * BookingQuery.categories uses and for the same reason: an empty set
     * would have to mean either all or none, and the caller that wants to
     * exclude a role (the ops console excludes ADMIN) needs to say so
     * rather than pass null and get everything.
     */
    Page<AccountSummary> searchAccounts(String text, java.util.Set<AccountRole> roles, Boolean blocked, Pageable pageable);

    /**
     * Blocks an account. Empty when no such account exists.
     * <p>
     * A reason is required by the caller rather than defaulted, and stored
     * with who did it and when - see AccountEntity and V8. Blocking a woman
     * off a women's safety platform is a contestable act, and who decided
     * it, when, and on what grounds has to be answerable months later.
     * <p>
     * Idempotent in effect but not in audit: blocking an already-blocked
     * account overwrites the reason and timestamp with the newer decision,
     * which is the more useful record of why it is blocked now.
     * <p>
     * Returns Optional rather than a Result because there is exactly one
     * failure worth distinguishing. AuthError
     * is internal to this module and not widened to the public interface
     * for one method.
     */
    Optional<AccountSummary> blockAccount(UUID accountId, UUID adminAccountId, String reason);

    /** Clears a block. Empty when no such account exists. */
    Optional<AccountSummary> unblockAccount(UUID accountId);

    /**
     * The when/who/why behind a block, or empty if this account is not
     * blocked (or does not exist). See {@link AccountBlock} for why this is
     * separate from the flag on AccountSummary.
     */
    Optional<AccountBlock> findBlockDetail(UUID accountId);
}
