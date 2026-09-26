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

/** Budget rows are read locked wherever spend is compared with the cap and written back. */
interface PromotionRepository extends JpaRepository<PromotionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PromotionEntity p where p.id = :id")
    Optional<PromotionEntity> findLockedById(@Param("id") UUID id);

    List<PromotionEntity> findByType(PromotionType type);

    List<PromotionEntity> findByCodeIsNullAndTypeNot(PromotionType type);

    Optional<PromotionEntity> findByCodeIgnoreCase(String code);

    List<PromotionEntity> findAllByOrderByCreatedAtDesc();
}
