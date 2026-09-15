package com.sheout.notifications.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {

    /** An account's inbox, newest first. */
    Page<NotificationLogEntity> findByRecipientAccountIdOrderByCreatedAtDesc(UUID recipientAccountId, Pageable pageable);

    long countByRecipientAccountIdAndReadAtIsNull(UUID recipientAccountId);

    /** Only the recipient's own - somebody else's notification is simply not found. */
    Optional<NotificationLogEntity> findByIdAndRecipientAccountId(UUID id, UUID recipientAccountId);

    @Modifying
    @Query("update NotificationLogEntity n set n.readAt = :at where n.recipientAccountId = :accountId and n.readAt is null")
    int markAllRead(@Param("accountId") UUID accountId, @Param("at") Instant at);
}
