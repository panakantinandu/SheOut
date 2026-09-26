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

interface IncentiveAwardRepository extends JpaRepository<IncentiveAwardEntity, UUID> {

    boolean existsByIncentiveIdAndBookingId(UUID incentiveId, UUID bookingId);

    long countByIncentiveId(UUID incentiveId);

    @Query("select count(distinct a.driverId) from IncentiveAwardEntity a where a.incentiveId = :incentiveId")
    long countDistinctDriversByIncentiveId(@Param("incentiveId") UUID incentiveId);
}
