package com.sheout.driververification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VerificationSampleReviewRepository extends JpaRepository<VerificationSampleReviewEntity, UUID> {

    List<VerificationSampleReviewEntity> findBySampleWeek(String sampleWeek);

    Optional<VerificationSampleReviewEntity> findByAccountIdAndSampleWeek(UUID accountId, String sampleWeek);
}
