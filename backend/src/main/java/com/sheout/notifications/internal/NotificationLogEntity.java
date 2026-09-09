package com.sheout.notifications.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Debugging/audit trail only - nothing reads this back to decide behavior
 * (contrast payments' PaymentEntity, which other logic depends on). One row
 * per delivery attempt, success or failure - see NotificationLogService's
 * Javadoc for why writing this row is safe even from an AFTER_COMMIT
 * listener.
 */
@Entity
@Table(name = "notification_log")
public class NotificationLogEntity extends BaseEntity {

    @Column(name = "recipient_account_id", nullable = false)
    private UUID recipientAccountId;

    /** Phone number for SMS, device token for push - null if send was never attempted (e.g. NO_RECIPIENT_ADDRESS). */
    @Column(name = "recipient_address")
    private String recipientAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    protected NotificationLogEntity() {
        // JPA
    }

    public NotificationLogEntity(UUID recipientAccountId, String recipientAddress, NotificationType type,
                                  NotificationChannelType channel, NotificationStatus status, String failureReason) {
        this.recipientAccountId = recipientAccountId;
        this.recipientAddress = recipientAddress;
        this.type = type;
        this.channel = channel;
        this.status = status;
        this.failureReason = failureReason;
    }

    public UUID getRecipientAccountId() {
        return recipientAccountId;
    }

    public String getRecipientAddress() {
        return recipientAddress;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationChannelType getChannel() {
        return channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
