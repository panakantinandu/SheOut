package com.sheout.payouts.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EarningHoldRepository extends JpaRepository<EarningHoldEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EarningHoldEntity> findLockedByBookingId(UUID bookingId);

    /** Holds whose window has passed and that no operator is keeping, for the release sweep. */
    List<EarningHoldEntity> findTop200ByStatusAndOperatorHoldFalseAndReleaseAtBefore(EarningHoldEntity.Status status, Instant now);
}
