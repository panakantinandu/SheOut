package com.sheout.users.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface CustomerProfileRepository extends JpaRepository<CustomerProfileEntity, UUID> {

    Optional<CustomerProfileEntity> findByAccountId(UUID accountId);
}
