package com.sheout.notifications.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One attempt to deliver a notification through one channel to one address.
 * The audit trail: nothing reads it back to decide behaviour, and it is never
 * shown to the person the notification was for - a provider's error text
 * names our providers and our failures, and nobody on the other end can act
 * on it.
 */
@Entity
@Table(name = "notification_deliveries")
public class NotificationDeliveryEntity extends BaseEntity {

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannelType channel;

    /** Phone number, email address or device token; null when there was none, or once erased. */
    @Column(name = "recipient_address", length = 512)
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected NotificationDeliveryEntity() {
        // JPA
    }

    public NotificationDeliveryEntity(UUID notificationId, NotificationChannelType channel, String recipientAddress,
                                      NotificationStatus status, String failureReason) {
        this.notificationId = notificationId;
        this.channel = channel;
        this.recipientAddress = recipientAddress;
        this.status = status;
        this.failureReason = failureReason;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public NotificationChannelType getChannel() {
        return channel;
    }

    public String getRecipientAddress() {
        return recipientAddress;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
