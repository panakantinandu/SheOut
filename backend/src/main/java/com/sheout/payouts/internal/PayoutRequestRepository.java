package com.sheout.payouts.internal;

import com.sheout.payouts.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PayoutRequestRepository extends JpaRepository<PayoutRequestEntity, UUID> {

    List<PayoutRequestEntity> findByDriverAccountIdOrderByCreatedAtDesc(UUID driverAccountId);

    Page<PayoutRequestEntity> findByStatus(PayoutStatus status, Pageable pageable);

    boolean existsByDriverAccountIdAndStatus(UUID driverAccountId, PayoutStatus status);

    /** Two operators marking the same request paid: one wins, the other sees ALREADY_PAID. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PayoutRequestEntity> findLockedById(UUID id);
}
