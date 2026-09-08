package com.sheout.driververification;

import java.util.Optional;
import java.util.UUID;

/**
 * Public read API for other modules. Booking/dispatch (not built yet) are
 * expected to call {@code findByAccountId} to gate "can this account book /
 * go online" - that enforcement lives in those modules, not here, since
 * they don't exist yet.
 */
public interface VerificationApi {

    Optional<VerificationSummary> findByAccountId(UUID accountId);
}
