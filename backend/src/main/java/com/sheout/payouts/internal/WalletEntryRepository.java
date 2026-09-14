package com.sheout.payouts.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface WalletEntryRepository extends JpaRepository<WalletEntryEntity, UUID> {

    /** Whether this capture has already been applied to a wallet - the idempotency check before the unique constraint. */
    boolean existsByPaymentIdAndType(UUID paymentId, WalletEntryEntity.Type type);
}
