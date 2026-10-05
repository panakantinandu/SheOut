package com.sheout.notifications.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reading an account's inbox, marking it read, and deleting from it. Every
 * method is scoped to the account passed in. Deleting hides a notification
 * from her and keeps the row - see NotificationLogEntity.deletedAt.
 */
@Service
public class NotificationInboxService {

    private final NotificationLogRepository notifications;

    public NotificationInboxService(NotificationLogRepository notifications) {
        this.notifications = notifications;
    }

    public Page<NotificationLogEntity> page(UUID accountId, Pageable pageable) {
        return notifications.findByRecipientAccountIdAndDeletedAtIsNullOrderByCreatedAtDesc(accountId, pageable);
    }

    public long unreadCount(UUID accountId) {
        return notifications.countByRecipientAccountIdAndReadAtIsNullAndDeletedAtIsNull(accountId);
    }

    /** Empty when the notification does not exist or is somebody else's - the caller cannot tell which. */
    @Transactional
    public Optional<NotificationLogEntity> markRead(UUID accountId, UUID notificationId) {
        return notifications.findByIdAndRecipientAccountIdAndDeletedAtIsNull(notificationId, accountId).map(notification -> {
            notification.markRead(Instant.now());
            return notifications.save(notification);
        });
    }

    @Transactional
    public void markAllRead(UUID accountId) {
        notifications.markAllRead(accountId, Instant.now());
    }

    /** False when there is no such notification in her inbox - somebody else's, or already deleted. */
    @Transactional
    public boolean delete(UUID accountId, UUID notificationId) {
        return notifications.findByIdAndRecipientAccountIdAndDeletedAtIsNull(notificationId, accountId).map(notification -> {
            notification.markDeleted(Instant.now());
            notifications.save(notification);
            return true;
        }).orElse(false);
    }

    /** Clears her whole inbox. How many were deleted. */
    @Transactional
    public int deleteAll(UUID accountId) {
        return notifications.deleteAll(accountId, Instant.now());
    }
}
