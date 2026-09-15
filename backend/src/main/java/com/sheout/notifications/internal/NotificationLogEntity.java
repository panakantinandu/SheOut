package com.sheout.notifications.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One notification to one account - what the in-app Notifications screen
 * lists. It records what SheOut told somebody, whether or not any copy of it
 * reached a phone: a partner with push switched off still finds the offer
 * she missed here.
 * <p>
 * How each copy was delivered (push to each device, email, SMS, each SOS
 * contact) is NotificationDeliveryEntity, one row per attempt. Until V19 this
 * table WAS the delivery attempts, which is why the screen used to show
 * nothing but "SMS - Failed".
 */
@Entity
@Table(name = "notification_log")
public class NotificationLogEntity extends BaseEntity {

    @Column(name = "recipient_account_id", nullable = false)
    private UUID recipientAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String body;

    /** A path inside the app the notification opens. Null for none. */
    @Column(length = 200)
    private String link;

    @Column(name = "read_at")
    private Instant readAt;

    protected NotificationLogEntity() {
        // JPA
    }

    public NotificationLogEntity(UUID recipientAccountId, NotificationType type, String title, String body, String link) {
        this.recipientAccountId = recipientAccountId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public UUID getRecipientAccountId() {
        return recipientAccountId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLink() {
        return link;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
