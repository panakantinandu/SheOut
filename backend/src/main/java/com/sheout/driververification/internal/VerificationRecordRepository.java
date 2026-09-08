package com.sheout.driververification.internal;

import com.sheout.driververification.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VerificationRecordRepository extends JpaRepository<VerificationRecordEntity, UUID> {

    Optional<VerificationRecordEntity> findByAccountId(UUID accountId);

    List<VerificationRecordEntity> findByGenderVerificationStatus(VerificationStatus status);
}
