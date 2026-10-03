package com.sheout.payments.internal.wallet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface RiderWalletEntryRepository extends JpaRepository<RiderWalletEntryEntity, UUID> {

    Page<RiderWalletEntryEntity> findByCustomerAccountIdOrderByCreatedAtDesc(UUID customerAccountId, Pageable pageable);

    boolean existsByTopupIdAndType(UUID topupId, RiderWalletEntryEntity.Type type);

    boolean existsByBookingIdAndType(UUID bookingId, RiderWalletEntryEntity.Type type);

    boolean existsByPaymentIdAndType(UUID paymentId, RiderWalletEntryEntity.Type type);

    boolean existsByRefundIdAndType(UUID refundId, RiderWalletEntryEntity.Type type);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(e.amount), 0) from RiderWalletEntryEntity e"
            + " where e.bookingId = :bookingId and e.type = :type")
    java.math.BigDecimal sumByBookingIdAndType(@org.springframework.data.repository.query.Param("bookingId") UUID bookingId,
                                               @org.springframework.data.repository.query.Param("type") RiderWalletEntryEntity.Type type);
}
