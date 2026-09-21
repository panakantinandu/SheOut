package com.sheout.driververification.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.AccountVerified;
import com.sheout.driververification.VerificationRejected;
import com.sheout.driververification.VerificationSubmitted;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.driververification.VerificationDropOff;
import com.sheout.driververification.VerificationTurnaround;
import com.sheout.sharedkernel.storage.DocumentRules;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VerificationService implements VerificationApi {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    /** How many recent reviews the typical time is taken from. */
    private static final int TURNAROUND_SAMPLE = 20;

    /**
     * Below this, there is no "typical" - there is a handful of reviews and
     * a target. Five is the point where a median stops being one person's
     * lunch break.
     */
    private static final int MIN_REVIEWS_FOR_MEASURED = 5;

    private final VerificationRecordRepository repository;
    private final VerificationFunnelRepository funnel;
    private final DocumentStorage documentStorage;
    private final DomainEventPublisher eventPublisher;
    private final int targetMinutes;

    public VerificationService(VerificationRecordRepository repository,
                                VerificationFunnelRepository funnel,
                                DocumentStorage documentStorage,
                                DomainEventPublisher eventPublisher,
                                // What operations commits to while there is
                                // nothing measured yet. Said as a target, not
                                // as a fact - see turnaroundFor.
                                @Value("${sheout.verification.target-turnaround-minutes:240}") int targetMinutes) {
        this.repository = repository;
        this.funnel = funnel;
        this.documentStorage = documentStorage;
        this.eventPublisher = eventPublisher;
        this.targetMinutes = targetMinutes;
    }

    /**
     * Reacts to a new account, rather than auth calling into this module
     * directly - this is the write-that-other-modules-react-to case the
     * shared-kernel event mechanism exists for.
     */
    @EventListener
    @Transactional
    public void onAccountRegistered(AccountRegistered event) {
        repository.save(new VerificationRecordEntity(event.accountId(), event.role()));
    }

    /**
     * Takes the identity document, and for a partner the vehicle's
     * registration certificate alongside it.
     * <p>
     * Both land in one call because they are evidence for one decision. Two
     * separate endpoints would let a partner submit half her case and sit in
     * the queue as a row an operator cannot action.
     */
    @Transactional
    /** The first thing wrong with either document, or null when both are usable. */
    private static VerificationError firstProblem(DocumentUpload... uploads) {
        for (DocumentUpload upload : uploads) {
            if (upload == null) {
                continue;
            }
            DocumentRules.Problem problem = DocumentRules.check(upload);
            if (problem == null) {
                continue;
            }
            return switch (problem) {
                case UNSUPPORTED_TYPE -> VerificationError.DOCUMENT_TYPE_UNSUPPORTED;
                case TOO_SMALL -> VerificationError.DOCUMENT_TOO_SMALL;
                case TOO_LARGE -> VerificationError.DOCUMENT_TOO_LARGE;
            };
        }
        return null;
    }

    public Result<VerificationSummary, VerificationError> submitDocument(
            UUID accountId, DocumentUpload upload, DocumentUpload rcUpload) {
        Optional<VerificationRecordEntity> found = repository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(VerificationError.RECORD_NOT_FOUND);
        }
        VerificationRecordEntity record = found.get();

        // Partners submit two documents, riders one, and the difference is
        // not an inconsistency: an operator reviewing a partner has to check
        // the registration number she typed against the vehicle she actually
        // owns, and there is nothing to cross-check for a rider who has no
        // vehicle. Asking a rider for an RC would be asking for a document
        // she cannot have.
        if (record.getRole() == AccountRole.DRIVER && rcUpload == null) {
            return Result.failure(VerificationError.RC_DOCUMENT_REQUIRED);
        }

        // Checked before a byte is stored, and for both documents, so a
        // partner is never told her ID was fine and her RC was not after
        // one of them has already been filed against her record.
        VerificationError badUpload = firstProblem(upload, rcUpload);
        if (badUpload != null) {
            return Result.failure(badUpload);
        }

        String storageKey;
        String rcStorageKey = null;
        try {
            storageKey = documentStorage.store(accountId, "aadhaar", upload);
            if (rcUpload != null) {
                rcStorageKey = documentStorage.store(accountId, "rc", rcUpload);
            }
        } catch (RuntimeException ex) {
            return Result.failure(VerificationError.STORAGE_FAILED);
        }

        record.setAadhaarDocumentKey(storageKey);
        if (rcStorageKey != null) {
            record.setRcDocumentKey(rcStorageKey);
        }
        if (record.getGenderVerificationStatus() == VerificationStatus.PENDING
                || record.getGenderVerificationStatus() == VerificationStatus.REJECTED) {
            record.setGenderVerificationStatus(VerificationStatus.UNDER_REVIEW);
        }
        // When it arrived, so the wait can be measured rather than guessed
        // at - see turnaround(). Re-submitting after a rejection restarts it,
        // because that is when the queue started waiting for this document.
        record.markDocumentSubmitted();
        repository.save(record);
        // Operations hears about it now, not when somebody next opens the
        // console: how long she waits is mostly how long it takes anybody to
        // notice, and that was nobody's job until this.
        eventPublisher.publish(new VerificationSubmitted(record.getAccountId(), record.getRole()));
        return Result.success(toSummary(record));
    }

    /**
     * Notes that she got this far, the first time she does.
     * <p>
     * Quiet by design: it records a step and returns. Nothing about the
     * document, nothing she typed, and no failure path that could stop her
     * uploading - an instrument that breaks the thing it measures is worse
     * than no instrument.
     */
    @Transactional
    public void recordFunnelStep(UUID accountId, AccountRole role, VerificationFunnelStep step) {
        if (funnel.existsByAccountIdAndStep(accountId, step)) {
            return;
        }
        try {
            funnel.save(new VerificationFunnelEntity(accountId, role, step));
        } catch (RuntimeException ex) {
            // Two taps at once, most likely, hitting the unique index. The
            // measurement is not worth an error in her face.
            log.debug("Could not record verification funnel step {}", step, ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationDropOff dropOff(AccountRole role, int windowDays) {
        Instant since = Instant.now().minus(Duration.ofDays(Math.max(1, windowDays)));
        return new VerificationDropOff(
                funnel.countReaching(role, VerificationFunnelStep.UPLOAD_VIEWED, since),
                funnel.countReaching(role, VerificationFunnelStep.DOCUMENT_CHOSEN, since),
                funnel.countAbandoned(role, VerificationFunnelStep.UPLOAD_VIEWED, since),
                windowDays);
    }

    /**
     * What to tell somebody who is waiting.
     * <p>
     * The median of the last few reviews, not the mean: one document
     * submitted on a Friday night and reviewed on Monday would otherwise
     * push the number everybody sees into days. Rounded up to the next
     * half hour, and never below a quarter of an hour, because "usually
     * about 3 minutes" invites a refresh loop and a complaint the first
     * time somebody waits twenty.
     * <p>
     * Below MIN_REVIEWS_FOR_MEASURED it returns the configured target
     * instead and says so: with three reviews behind us, "usually" is a
     * guess wearing a number's clothes.
     */
    @Override
    @Transactional(readOnly = true)
    public VerificationTurnaround turnaroundFor(AccountRole role) {
        List<ReviewWindow> reviews = repository.recentReviews(role, PageRequest.of(0, TURNAROUND_SAMPLE));
        if (reviews.size() < MIN_REVIEWS_FOR_MEASURED) {
            return new VerificationTurnaround(targetMinutes, false, reviews.size());
        }
        List<Long> sorted = reviews.stream().map(ReviewWindow::seconds).sorted().toList();
        double median = sorted.size() % 2 == 1
                ? sorted.get(sorted.size() / 2)
                : (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0;
        int minutes = (int) Math.ceil(median / 60.0);
        int rounded = Math.max(15, (int) (Math.ceil(minutes / 30.0) * 30));
        return new VerificationTurnaround(rounded, true, sorted.size());
    }

    /** The operator's own words, or something honest when they left it blank. */
    private static String rejectionReasonFor(String reason) {
        return reason == null || reason.isBlank()
                ? "The document could not be accepted. Please submit a clearer photo of a government ID."
                : reason.trim();
    }

    @Transactional
    public Result<VerificationSummary, VerificationError> reviewGenderVerification(
            UUID accountId, UUID adminAccountId, VerificationStatus decision, String reason) {
        if (decision != VerificationStatus.VERIFIED && decision != VerificationStatus.REJECTED) {
            return Result.failure(VerificationError.INVALID_DECISION);
        }
        Optional<VerificationRecordEntity> found = repository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(VerificationError.RECORD_NOT_FOUND);
        }
        VerificationRecordEntity record = found.get();
        if (record.getGenderVerificationStatus() != VerificationStatus.UNDER_REVIEW) {
            return Result.failure(VerificationError.NOT_UNDER_REVIEW);
        }

        record.setGenderVerificationStatus(decision);
        record.recordReview(adminAccountId.toString(), decision == VerificationStatus.REJECTED ? reason : null);
        repository.save(record);
        publishIfFullyVerified(record);
        if (decision == VerificationStatus.REJECTED) {
            // She is told, with the reason the operator gave. Without this
            // the app kept saying "being reviewed" forever and the reason
            // stayed in the database.
            eventPublisher.publish(new VerificationRejected(
                    record.getAccountId(), record.getRole(), rejectionReasonFor(reason)));
        }
        return Result.success(toSummary(record));
    }

    /**
     * Driver-only, admin-set with no submission step of its own (per spec:
     * "manual, admin-set") - so no UNDER_REVIEW gate, just PENDING/REJECTED
     * moving to VERIFIED/REJECTED directly.
     */
    @Transactional
    public Result<VerificationSummary, VerificationError> reviewPoliceVerification(
            UUID accountId, UUID adminAccountId, VerificationStatus decision) {
        if (decision != VerificationStatus.VERIFIED && decision != VerificationStatus.REJECTED) {
            return Result.failure(VerificationError.INVALID_DECISION);
        }
        Optional<VerificationRecordEntity> found = repository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(VerificationError.RECORD_NOT_FOUND);
        }
        VerificationRecordEntity record = found.get();
        if (record.getRole() != AccountRole.DRIVER) {
            return Result.failure(VerificationError.POLICE_VERIFICATION_NOT_APPLICABLE);
        }

        record.setPoliceVerificationStatus(decision);
        repository.save(record);
        publishIfFullyVerified(record);
        return Result.success(toSummary(record));
    }

    public List<VerificationSummary> findByGenderStatus(VerificationStatus status) {
        return repository.findByGenderVerificationStatus(status).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Optional<VerificationSummary> findByAccountId(UUID accountId) {
        return repository.findByAccountId(accountId).map(this::toSummary);
    }

    @Override
    public List<VerificationSummary> findAwaitingReview() {
        return repository
                .findAwaitingReview(VerificationStatus.UNDER_REVIEW, VerificationStatus.PENDING)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Optional<String> findDocumentUrl(UUID accountId) {
        return repository.findByAccountId(accountId)
                .map(VerificationRecordEntity::getAadhaarDocumentKey)
                .map(documentStorage::resolveUrl);
    }

    @Override
    public Optional<String> findRcDocumentUrl(UUID accountId) {
        return repository.findByAccountId(accountId)
                .map(VerificationRecordEntity::getRcDocumentKey)
                .map(documentStorage::resolveUrl);
    }

    private void publishIfFullyVerified(VerificationRecordEntity record) {
        if (record.isFullyVerified()) {
            eventPublisher.publish(new AccountVerified(record.getAccountId(), record.getRole()));
        }
    }

    private VerificationSummary toSummary(VerificationRecordEntity record) {
        return new VerificationSummary(
                record.getAccountId(),
                record.getRole(),
                record.getGenderVerificationStatus(),
                record.getPoliceVerificationStatus(),
                record.getAadhaarDocumentKey() != null,
                record.getGenderVerificationStatus() == VerificationStatus.REJECTED ? record.getRejectionReason() : null,
                record.getUpdatedAt()
        );
    }
}
