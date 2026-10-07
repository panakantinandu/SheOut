package com.sheout.driververification.internal;

import com.sheout.driververification.CheckType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface VerificationCheckRepository extends JpaRepository<VerificationCheckEntity, UUID> {

    List<VerificationCheckEntity> findByAccountId(UUID accountId);

    Optional<VerificationCheckEntity> findByAccountIdAndType(UUID accountId, CheckType type);

    /** Decided in a window, for the weekly sample and override rates. */
    List<VerificationCheckEntity> findByDecidedAtBetween(Instant from, Instant to);
}
