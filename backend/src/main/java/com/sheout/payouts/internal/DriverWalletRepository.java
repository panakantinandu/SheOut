package com.sheout.payouts.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface DriverWalletRepository extends JpaRepository<DriverWalletEntity, UUID> {

    Optional<DriverWalletEntity> findByDriverAccountId(UUID driverAccountId);

    /**
     * Every change to a balance reads the row under this lock first, so two
     * payout requests in the same instant cannot both pass the "enough
     * balance" check against the same money.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DriverWalletEntity> findLockedByDriverAccountId(UUID driverAccountId);

    /**
     * Creates the wallet if this partner has none, and does nothing if she
     * does. An insert-then-catch would not work: in Postgres a failed insert
     * aborts the whole transaction, including the capture it runs inside.
     * ON CONFLICT DO NOTHING cannot fail on a concurrent create.
     */
    @Modifying
    @Query(value = """
            insert into driver_wallets (id, driver_account_id, total_earned, cash_collected, total_paid_out,
                                        pending_payouts, version, created_at, updated_at)
            values (gen_random_uuid(), :driverId, 0, 0, 0, 0, 0, now(), now())
            on conflict (driver_account_id) do nothing
            """, nativeQuery = true)
    void createIfAbsent(@Param("driverId") UUID driverAccountId);
}
