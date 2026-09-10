package com.sheout.notifications.internal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {

    /** Newest first, capped by the caller - see NotificationLogController. */
    List<NotificationLogEntity> findByRecipientAccountIdOrderByCreatedAtDesc(UUID recipientAccountId, Pageable pageable);
}
