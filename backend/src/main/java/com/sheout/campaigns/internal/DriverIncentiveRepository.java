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

interface DriverIncentiveRepository extends JpaRepository<DriverIncentiveEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from DriverIncentiveEntity i where i.id = :id")
    Optional<DriverIncentiveEntity> findLockedById(@Param("id") UUID id);

    List<DriverIncentiveEntity> findAllByOrderByCreatedAtDesc();
}
