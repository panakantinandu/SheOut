package com.sheout.campaigns.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ReferralRepository extends JpaRepository<ReferralEntity, UUID> {

    Optional<ReferralEntity> findByRefereeAccountId(UUID refereeAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReferralEntity r where r.id = :id")
    Optional<ReferralEntity> findLockedById(@Param("id") UUID id);

    List<ReferralEntity> findByReferrerAccountId(UUID referrerAccountId);

    /** Friends whose referral earned this referrer something - what her limit counts. */
    @Query("select count(r) from ReferralEntity r where r.referrerAccountId = :referrer "
            + "and r.status = com.sheout.campaigns.internal.ReferralEntity.Status.COMPLETED and r.referrerReward > 0")
    long countRewardedFor(@Param("referrer") UUID referrer);
}
