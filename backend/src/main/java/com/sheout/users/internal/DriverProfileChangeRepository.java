package com.sheout.users.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface DriverProfileChangeRepository extends JpaRepository<DriverProfileChangeEntity, UUID> {

    Optional<DriverProfileChangeEntity> findFirstByAccountIdAndStatus(UUID accountId, DriverProfileChangeEntity.Status status);

    Optional<DriverProfileChangeEntity> findFirstByAccountIdOrderByRequestedAtDesc(UUID accountId);

    List<DriverProfileChangeEntity> findByStatusOrderByRequestedAtAsc(DriverProfileChangeEntity.Status status);
}
