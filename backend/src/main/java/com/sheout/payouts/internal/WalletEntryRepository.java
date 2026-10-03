package com.sheout.payouts.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface WalletEntryRepository extends JpaRepository<WalletEntryEntity, UUID> {

    /** Whether this capture has already been applied to a wallet - the idempotency check before the unique constraint. */
    boolean existsByPaymentIdAndType(UUID paymentId, WalletEntryEntity.Type type);

    boolean existsByIncentiveAwardId(UUID incentiveAwardId);

    /** Her share per trip: the EARNING entries credited for it, summed. */
    @org.springframework.data.jpa.repository.Query("""
            select new com.sheout.payouts.TripEarning(e.bookingId, sum(e.amount))
            from WalletEntryEntity e
            where e.driverAccountId = :driver
              and e.type = :type
              and e.bookingId is not null
            group by e.bookingId
            """)
    java.util.List<com.sheout.payouts.TripEarning> earningsByTrip(
            @org.springframework.data.repository.query.Param("driver") UUID driverAccountId,
            @org.springframework.data.repository.query.Param("type") WalletEntryEntity.Type type);
}
