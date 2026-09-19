package com.sheout.payments.internal.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface RiderWalletEntryRepository extends JpaRepository<RiderWalletEntryEntity, UUID> {

    Page<RiderWalletEntryEntity> findByCustomerAccountIdOrderByCreatedAtDesc(UUID customerAccountId, Pageable pageable);

    boolean existsByTopupIdAndType(UUID topupId, RiderWalletEntryEntity.Type type);

    boolean existsByBookingIdAndType(UUID bookingId, RiderWalletEntryEntity.Type type);
}
