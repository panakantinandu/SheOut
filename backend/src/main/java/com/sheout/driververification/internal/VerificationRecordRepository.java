package com.sheout.driververification.internal;

import com.sheout.driververification.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VerificationRecordRepository extends JpaRepository<VerificationRecordEntity, UUID> {

    Optional<VerificationRecordEntity> findByAccountId(UUID accountId);

    List<VerificationRecordEntity> findByGenderVerificationStatus(VerificationStatus status);

    /**
     * Awaiting review on either check. Both parameters are the same status
     * in practice; they stay separate so the derived query name matches the
     * two columns it spans.
     */
    List<VerificationRecordEntity> findByGenderVerificationStatusOrPoliceVerificationStatusOrderByUpdatedAtDesc(
            VerificationStatus genderStatus, VerificationStatus policeStatus);
}
