package com.sheout.payments.internal.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface WalletTopupRepository extends JpaRepository<WalletTopupEntity, UUID> {

    Optional<WalletTopupEntity> findByRazorpayOrderId(String razorpayOrderId);

    /** Checkout verify and the webhook can arrive together; each re-reads the top-up under this lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletTopupEntity> findLockedById(UUID id);
}
