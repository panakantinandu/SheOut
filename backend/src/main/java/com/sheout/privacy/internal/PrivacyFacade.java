package com.sheout.privacy.internal;

import com.sheout.auth.AccountRole;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * What the privacy controller calls. Exists so the web package does not see
 * the services' package-private internals - the same split every other
 * module keeps between internal and internal.web.
 */
@Component
public class PrivacyFacade {

    private final DataExportService exports;
    private final AccountDeletionService deletions;

    PrivacyFacade(DataExportService exports, AccountDeletionService deletions) {
        this.exports = exports;
        this.deletions = deletions;
    }

    public Optional<DataExport> export(UUID accountId, AccountRole role) {
        return exports.exportFor(accountId, role);
    }

    public DeletionOutcome deleteAccount(UUID accountId, AccountRole role) {
        AccountDeletionService.Result result = deletions.delete(accountId, role);
        return new DeletionOutcome(result.outcome() == AccountDeletionService.Outcome.DELETED,
                result.requestedAt(), result.completedAt());
    }

    public record DeletionOutcome(boolean deleted, Instant requestedAt, Instant completedAt) {
    }
}
