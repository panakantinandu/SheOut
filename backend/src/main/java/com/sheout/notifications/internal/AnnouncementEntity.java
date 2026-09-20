package com.sheout.notifications.internal;

import com.sheout.notifications.Announcement;
import com.sheout.notifications.AnnouncementAudience;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One broadcast, and what actually went out - see V31. */
@Entity
@Table(name = "announcements")
class AnnouncementEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementAudience audience;

    @Column(name = "sent_by", nullable = false)
    private UUID sentBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "push_accepted", nullable = false)
    private int pushAccepted;

    @Column(name = "push_failed", nullable = false)
    private int pushFailed;

    @Column(name = "emails_sent", nullable = false)
    private int emailsSent;

    @Column(name = "emails_failed", nullable = false)
    private int emailsFailed;

    @Column(name = "sms_sent", nullable = false)
    private int smsSent;

    @Column(name = "sms_failed", nullable = false)
    private int smsFailed;

    @Column(name = "sms_requested", nullable = false)
    private boolean smsRequested;

    @Column(length = 500)
    private String note;

    protected AnnouncementEntity() {
    }

    AnnouncementEntity(String title, String body, AnnouncementAudience audience, boolean smsRequested, UUID sentBy) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.body = body;
        this.audience = audience;
        this.smsRequested = smsRequested;
        this.sentBy = sentBy;
        this.createdAt = Instant.now();
    }

    void recordPush(int accepted, int failed) {
        this.pushAccepted = accepted;
        this.pushFailed = failed;
    }

    void recordEmail(int sent, int failed) {
        this.emailsSent = sent;
        this.emailsFailed = failed;
    }

    void recordSms(int sent, int failed) {
        this.smsSent = sent;
        this.smsFailed = failed;
    }

    void note(String note) {
        this.note = note == null || note.length() <= 500 ? note : note.substring(0, 500);
    }

    Announcement toSummary() {
        return new Announcement(id, title, body, audience, createdAt, pushAccepted, pushFailed,
                emailsSent, emailsFailed, smsSent, smsFailed, smsRequested, note);
    }
}
