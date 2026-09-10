package com.sheout.driververification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read API for other modules. Booking/dispatch call
 * {@code findByAccountId} to gate "can this account book / go online" -
 * that enforcement lives in those modules, not here.
 * <p>
 * The review-queue and document-URL methods below exist for the admin
 * module. Before them, the only way to build a review queue was
 * VerificationService.findByGenderStatus, which is internal - admin would
 * have had to import {@code driververification.internal} to get it.
 * Reviewing (approve/reject) is deliberately NOT here: it already has a
 * home on AdminVerificationController in this module, and routing it
 * through a second interface would give the same transition two entry
 * points to keep in step.
 */
public interface VerificationApi {

    Optional<VerificationSummary> findByAccountId(UUID accountId);

    /**
     * Every account awaiting review on EITHER status, newest-updated first.
     * One method rather than two (gender/police) because the queue is one
     * list to an operator - a driver blocking on either check is one row of
     * work, and returning them separately would make callers merge and
     * de-duplicate.
     * <p>
     * Note there is no SUBMITTED status to match: VerificationStatus
     * collapses submission into UNDER_REVIEW (see its Javadoc), so
     * "SUBMITTED or UNDER_REVIEW" is exactly UNDER_REVIEW.
     */
    List<VerificationSummary> findAwaitingReview();

    /**
     * Something an admin reviewer can open for this account's uploaded
     * document, or empty if nothing has been submitted. Returns a resolved
     * URL rather than the storage key: the key is this module's private
     * detail (see DocumentStorage), and a caller holding one could do
     * nothing with it anyway.
     */
    Optional<String> findDocumentUrl(UUID accountId);
}
