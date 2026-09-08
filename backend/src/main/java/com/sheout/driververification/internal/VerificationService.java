package com.sheout.driververification.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.driververification.AccountVerified;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.driververification.internal.storage.DocumentStorage;
import com.sheout.driververification.internal.storage.DocumentUpload;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class VerificationService implements VerificationApi {

    private final VerificationRecordRepository repository;
    private final DocumentStorage documentStorage;
    private final DomainEventPublisher eventPublisher;

    public VerificationService(VerificationRecordRepository repository,
                                DocumentStorage documentStorage,
                                DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.documentStorage = documentStorage;
        this.eventPublisher = eventPublisher;
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

    @Transactional
    public Result<VerificationSummary, VerificationError> submitDocument(UUID accountId, DocumentUpload upload) {
        Optional<VerificationRecordEntity> found = repository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Result.failure(VerificationError.RECORD_NOT_FOUND);
        }
        VerificationRecordEntity record = found.get();

        String storageKey;
        try {
            storageKey = documentStorage.store(accountId, "aadhaar", upload);
        } catch (RuntimeException ex) {
            return Result.failure(VerificationError.STORAGE_FAILED);
        }

        record.setAadhaarDocumentKey(storageKey);
        if (record.getGenderVerificationStatus() == VerificationStatus.PENDING
                || record.getGenderVerificationStatus() == VerificationStatus.REJECTED) {
            record.setGenderVerificationStatus(VerificationStatus.UNDER_REVIEW);
        }
        repository.save(record);
        return Result.success(toSummary(record));
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
                record.getUpdatedAt()
        );
    }
}
