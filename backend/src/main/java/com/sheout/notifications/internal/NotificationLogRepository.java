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

/** Every inbox query leaves out what she deleted (deletedAt): hidden from her, kept as the record. */
public interface NotificationLogRepository extends JpaRepository<NotificationLogEntity, UUID> {

    /** An account's inbox, newest first. */
    Page<NotificationLogEntity> findByRecipientAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID recipientAccountId, Pageable pageable);

    long countByRecipientAccountIdAndReadAtIsNullAndDeletedAtIsNull(UUID recipientAccountId);

    /** Only the recipient's own, and not one she deleted - anything else is simply not found. */
    Optional<NotificationLogEntity> findByIdAndRecipientAccountIdAndDeletedAtIsNull(UUID id, UUID recipientAccountId);

    @Modifying
    @Query("update NotificationLogEntity n set n.readAt = :at where n.recipientAccountId = :accountId and n.readAt is null and n.deletedAt is null")
    int markAllRead(@Param("accountId") UUID accountId, @Param("at") Instant at);

    /** Her whole inbox, deleted at once. */
    @Modifying
    @Query("update NotificationLogEntity n set n.deletedAt = :at, n.readAt = coalesce(n.readAt, :at) "
            + "where n.recipientAccountId = :accountId and n.deletedAt is null")
    int deleteAll(@Param("accountId") UUID accountId, @Param("at") Instant at);
}
