package com.sheout.marketplace.internal;

import com.sheout.marketplace.SellerStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface SellerProfileRepository extends JpaRepository<SellerProfileEntity, UUID>, JpaSpecificationExecutor<SellerProfileEntity> {

    Optional<SellerProfileEntity> findByAccountId(UUID accountId);

    /**
     * Locked for every change of status. An operator deciding, the fee's
     * webhook and her own Checkout confirming can all arrive together; each
     * reads the status under this lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SellerProfileEntity> findLockedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SellerProfileEntity> findLockedByAccountId(UUID accountId);

    long countByStatus(SellerStatus status);

    /** Every shop on a monthly plan that may need a reminder or to be hidden: live or lapsed, approved. */
    java.util.List<SellerProfileEntity> findByStatusInAndPaidUntilIsNotNull(java.util.Collection<SellerStatus> statuses);

    /** Every example shop - see SampleShops. */
    java.util.List<SellerProfileEntity> findBySampleTrue();
}
