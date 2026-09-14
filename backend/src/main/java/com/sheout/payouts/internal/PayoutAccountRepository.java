package com.sheout.payouts.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PayoutAccountRepository extends JpaRepository<PayoutAccountEntity, UUID> {

    Optional<PayoutAccountEntity> findByDriverAccountId(UUID driverAccountId);
}
