package com.sheout.auth;

import java.util.Optional;

/**
 * Request-scoped access to who's calling, populated from the request's
 * bearer token by auth's internal request filter and cleared at the end of
 * every request. Any module's controller can read this - it's the one
 * piece of auth-internal machinery deliberately exposed publicly, since
 * "who is the current caller" is a legitimate cross-module concern (e.g.
 * driver-verification's admin-only review endpoints check
 * {@code CurrentAccountContext.get()} for {@code AccountRole.ADMIN}).
 * <p>
 * Presence of a value only means the token was valid and unexpired - it is
 * not re-checked against the database on every request. Absence means the
 * caller is unauthenticated; each endpoint decides for itself whether that's
 * allowed (OTP request/verify must work with no token at all).
 */
public final class CurrentAccountContext {

    private static final ThreadLocal<CurrentAccount> CURRENT = new ThreadLocal<>();

    private CurrentAccountContext() {
    }

    public static Optional<CurrentAccount> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void set(CurrentAccount account) {
        CURRENT.set(account);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
