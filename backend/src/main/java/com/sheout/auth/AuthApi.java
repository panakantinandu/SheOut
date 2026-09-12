package com.sheout.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
     * Grants ADMIN to the existing account holding this phone number, or
     * returns empty if no such account exists. Idempotent: an account that
     * is already ADMIN is returned unchanged.
     * <p>
     * This is the ONLY way an ADMIN account can come into being. Signup
     * deliberately refuses role=ADMIN (see AuthController's
     * requireSelfServiceRole, added after that was found to be a live
     * privilege-escalation hole), which left no path at all - admin
     * endpoints existed but nothing could legitimately reach them. The
     * caller is admin's own startup bootstrap, driven by a deploy-time
     * env var rather than by anything a request can influence.
     * <p>
     * An already-issued token keeps its old role claim, so a promoted
     * account must sign in again before it can call admin endpoints.
     */
    Optional<AccountSummary> grantAdminRole(String phoneNumber);

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
     * failure worth distinguishing, matching grantAdminRole above. AuthError
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
