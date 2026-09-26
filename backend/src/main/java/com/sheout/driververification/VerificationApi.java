package com.sheout.driververification;

import com.sheout.auth.AccountRole;

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
     * How long reviews of this kind have actually been taking lately, for
     * the screen that tells somebody how long she is waiting.
     * <p>
     * Measured from real reviews once there are enough of them, and a
     * configured target before that - the answer says which it is, because
     * "usually" and "we aim to" are different promises.
     */
    VerificationTurnaround turnaroundFor(AccountRole role);

    /**
     * How many people reached the identity screen recently and never sent
     * anything - the measurement that says whether this step is a problem,
     * rather than an argument about whether it might be.
     */
    VerificationDropOff dropOff(AccountRole role, int windowDays);

    /**
     * Every account with outstanding review work, newest-updated first. One
     * method rather than two (gender/police) because the queue is one list
     * to an operator - a driver blocking on either check is one row of
     * work, and returning them separately would make callers merge and
     * de-duplicate.
     * <p>
     * The two checks are matched on different statuses, which looks
     * inconsistent but is not. Gender review is driver-initiated:
     * submitDocument moves it PENDING -&gt; UNDER_REVIEW, so PENDING there
     * means "nothing submitted yet" and there is genuinely nothing to
     * review. Police review has no submission step at all - nothing ever
     * sets it to UNDER_REVIEW (see VerificationService.reviewPoliceVerification,
     * which only accepts VERIFIED/REJECTED), so a driver sits at PENDING
     * from signup until an admin decides. Matching police on UNDER_REVIEW
     * would therefore match nothing, and let every driver awaiting a police
     * check fall out of the queue unseen.
     * <p>
     * Police PENDING alone would swing the other way and list every account
     * that ever signed up, burying real work under dormant ones, so it
     * counts only once a document has actually been submitted. The effect
     * is that a driver enters the queue when they submit and leaves it when
     * BOTH checks are decided - including the window after gender is
     * approved but police is still outstanding, which is precisely where
     * they used to disappear.
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

    /**
     * The vehicle registration certificate photo for this account, or empty
     * if none was submitted - which is every rider, and every partner whose
     * account predates the requirement.
     * <p>
     * A second method rather than widening the one above, so a caller that
     * only wants the identity document is not handed the other by accident.
     * An operator reads them together; nothing else should read either.
     */
    Optional<String> findRcDocumentUrl(UUID accountId);

    /** Empty when no live selfie is on file - every submission before it was required. */
    Optional<LiveSelfie> findLiveSelfie(UUID accountId);
}
