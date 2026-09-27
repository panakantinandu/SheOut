package com.sheout.campaigns.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface ReferralCodeRepository extends JpaRepository<ReferralCodeEntity, UUID> {

    Optional<ReferralCodeEntity> findByAccountId(UUID accountId);

    Optional<ReferralCodeEntity> findByCode(String code);

    boolean existsByCode(String code);

    /** Locked while a referrer's reward is decided, so two friends finishing at once cannot both pass her limit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReferralCodeEntity c where c.accountId = :accountId")
    Optional<ReferralCodeEntity> findLockedByAccountId(@Param("accountId") UUID accountId);
}
