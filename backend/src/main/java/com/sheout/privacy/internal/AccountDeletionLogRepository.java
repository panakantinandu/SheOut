package com.sheout.privacy.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AccountDeletionLogRepository extends JpaRepository<AccountDeletionLogEntity, UUID> {
}
