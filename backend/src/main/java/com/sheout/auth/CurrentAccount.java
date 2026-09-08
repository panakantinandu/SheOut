package com.sheout.auth;

import java.util.UUID;

/**
 * The caller identified by a valid access token on the current request.
 * See {@link CurrentAccountContext}.
 */
public record CurrentAccount(UUID accountId, AccountRole role) {
}
