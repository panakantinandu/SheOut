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

interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemptionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PromotionRedemptionEntity r where r.bookingId = :bookingId")
    Optional<PromotionRedemptionEntity> findLockedByBookingId(@Param("bookingId") UUID bookingId);

    long countByPromotionIdAndAccountIdAndStatusIn(UUID promotionId, UUID accountId,
                                                   Collection<PromotionRedemptionEntity.Status> statuses);

    long countByGrantIdAndStatus(UUID grantId, PromotionRedemptionEntity.Status status);

    long countByPromotionIdAndStatus(UUID promotionId, PromotionRedemptionEntity.Status status);
}
