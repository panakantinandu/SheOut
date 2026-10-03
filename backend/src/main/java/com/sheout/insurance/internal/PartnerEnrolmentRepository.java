package com.sheout.insurance.internal;

import com.sheout.insurance.EnrolmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PartnerEnrolmentRepository extends JpaRepository<PartnerEnrolmentEntity, UUID> {

    List<PartnerEnrolmentEntity> findByAccountId(UUID accountId);

    List<PartnerEnrolmentEntity> findAllByOrderByCreatedAtDesc();

    Optional<PartnerEnrolmentEntity> findFirstByAccountIdAndPolicyIdAndStatusNot(UUID accountId, UUID policyId,
                                                                                 EnrolmentStatus status);

    List<PartnerEnrolmentEntity> findByEnrolledOnBetween(LocalDate from, LocalDate to);

    List<PartnerEnrolmentEntity> findByExitedOnBetween(LocalDate from, LocalDate to);
}
