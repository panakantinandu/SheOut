package com.sheout.notifications.internal;

import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.sharedkernel.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes notifications and their delivery attempts.
 * <p>
 * Its own bean, with REQUIRES_NEW, so each write commits on its own no matter
 * where the caller is: an AFTER_COMMIT listener still has transaction
 * synchronization active on its thread, and a save that merely joined it
 * would return an id and commit nothing - the bug payments'
 * BookingCompletedListener hit, confirmed against Postgres. REQUIRES_NEW only
 * takes effect through the Spring proxy, which is why the writes live here
 * and not in the dispatcher that calls them.
 * <p>
 * A delivery row is written per attempt, immediately, so a notification whose
 * later sends hang or fail still shows every attempt made before that.
 */
@Service
public class NotificationLogService {

    private final NotificationLogRepository notifications;
    private final NotificationDeliveryRepository deliveries;

    public NotificationLogService(NotificationLogRepository notifications, NotificationDeliveryRepository deliveries) {
        this.notifications = notifications;
        this.deliveries = deliveries;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordNotification(UUID recipientAccountId, NotificationType type, OutboundMessage message) {
        return notifications.save(new NotificationLogEntity(
                recipientAccountId, type, truncate(message.title(), 120), truncate(message.body(), 500), message.link()))
                .getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDelivery(UUID notificationId, NotificationChannelType channel, String recipientAddress,
                               Result<Void, SendFailure> outcome) {
        deliveries.save(new NotificationDeliveryEntity(
                notificationId, channel, recipientAddress,
                outcome.isSuccess() ? NotificationStatus.SENT : NotificationStatus.FAILED,
                outcome.isFailure() ? outcome.error().toString() : null));
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
