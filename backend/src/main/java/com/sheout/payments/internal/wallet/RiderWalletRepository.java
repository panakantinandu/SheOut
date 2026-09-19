package com.sheout.payments.internal.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface RiderWalletRepository extends JpaRepository<RiderWalletEntity, UUID> {

    Optional<RiderWalletEntity> findByCustomerAccountId(UUID customerAccountId);

    /** Every balance change reads the row through this, so two changes to one wallet run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RiderWalletEntity> findLockedByCustomerAccountId(UUID customerAccountId);

    /**
     * Creates her wallet if she has none. ON CONFLICT rather than
     * insert-and-catch, for the reason DriverWalletRepository gives: a failed
     * insert aborts the whole Postgres transaction, including the payment it
     * runs inside.
     */
    @Modifying
    @Query(value = """
            insert into rider_wallets (id, customer_account_id, balance, version, created_at, updated_at)
            values (gen_random_uuid(), :customerId, 0, 0, now(), now())
            on conflict (customer_account_id) do nothing
            """, nativeQuery = true)
    void createIfAbsent(@Param("customerId") UUID customerAccountId);
}
