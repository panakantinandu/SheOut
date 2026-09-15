package com.sheout.notifications.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reading an account's inbox and marking it read. Every method is scoped to the account passed in. */
@Service
public class NotificationInboxService {

    private final NotificationLogRepository notifications;

    public NotificationInboxService(NotificationLogRepository notifications) {
        this.notifications = notifications;
    }

    public Page<NotificationLogEntity> page(UUID accountId, Pageable pageable) {
        return notifications.findByRecipientAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    public long unreadCount(UUID accountId) {
        return notifications.countByRecipientAccountIdAndReadAtIsNull(accountId);
    }

    /** Empty when the notification does not exist or is somebody else's - the caller cannot tell which. */
    @Transactional
    public Optional<NotificationLogEntity> markRead(UUID accountId, UUID notificationId) {
        return notifications.findByIdAndRecipientAccountId(notificationId, accountId).map(notification -> {
            notification.markRead(Instant.now());
            return notifications.save(notification);
        });
    }

    @Transactional
    public void markAllRead(UUID accountId) {
        notifications.markAllRead(accountId, Instant.now());
    }
}
