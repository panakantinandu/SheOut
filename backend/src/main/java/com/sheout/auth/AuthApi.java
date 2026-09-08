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
}
