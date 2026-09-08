package com.sheout.auth;

import java.util.UUID;

public record AuthenticatedSession(
        String accessToken,
        UUID accountId,
        AccountRole role,
        boolean newAccount
) {
}
