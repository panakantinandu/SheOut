package com.sheout.driververification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface VerificationAuditRepository extends JpaRepository<VerificationAuditEntity, UUID> {

    List<VerificationAuditEntity> findByAccountIdOrderByAtDesc(UUID accountId);
}
