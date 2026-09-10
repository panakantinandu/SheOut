package com.sheout.auth;

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
}
