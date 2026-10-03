package com.sheout.driververification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PoliceVerificationRepository extends JpaRepository<PoliceVerificationEntity, UUID> {

    List<PoliceVerificationEntity> findByAccountIdOrderByDecidedAtDesc(UUID accountId);
}
