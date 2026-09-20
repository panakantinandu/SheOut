package com.sheout.notifications;

import java.util.List;
import java.util.UUID;

/**
 * Broadcasts, for the operations console.
 * <p>
 * The console calls this; it never touches notifications' own tables, push
 * topics or the mail server. Who is allowed to broadcast is the admin
 * module's business, and is checked before any of this is reached.
 */
public interface AnnouncementApi {

    /**
     * Sends one message to an audience and records what went out.
     * <p>
     * Push goes to the audience's FCM topic - one call, not a loop over
     * tokens. Email goes out in batches, paced for the mail provider. SMS is
     * sent only when sendSms is true, because every SMS must match a
     * DLT-approved template and is charged per message; it is for a safety
     * advisory, not for news.
     */
    Announcement broadcast(String title, String body, AnnouncementAudience audience, boolean sendSms, UUID sentBy);

    /** What has been broadcast, newest first. */
    List<Announcement> history(int limit);
}
