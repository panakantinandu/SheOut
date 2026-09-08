package com.sheout.users.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface DriverProfileRepository extends JpaRepository<DriverProfileEntity, UUID> {

    Optional<DriverProfileEntity> findByAccountId(UUID accountId);
}
