package com.sheout.campaigns.internal;

import com.sheout.campaigns.PromotionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PromotionGrantRepository extends JpaRepository<PromotionGrantEntity, UUID> {

    List<PromotionGrantEntity> findByAccountId(UUID accountId);

    Optional<PromotionGrantEntity> findByPromotionIdAndAccountId(UUID promotionId, UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from PromotionGrantEntity g where g.id = :id")
    Optional<PromotionGrantEntity> findLockedById(@Param("id") UUID id);

    List<PromotionGrantEntity> findByPromotionIdIn(Collection<UUID> promotionIds);

    long countByPromotionId(UUID promotionId);
}
