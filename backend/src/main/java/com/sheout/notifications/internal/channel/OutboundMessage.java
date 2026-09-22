package com.sheout.notifications.internal.channel;

/**
 * One notification as a channel needs it. Each channel takes what suits it:
 * push shows title and body and opens {@code link}; SMS sends {@link #smsText()};
 * email uses the title as the subject.
 *
 * @param link    a path inside the app it is going to, starting with "/" -
 *                never a full URL, so a notification can only ever open SheOut
 * @param tag     collapses repeats on the device: a second notification with
 *                the same tag replaces the first instead of stacking. Null
 *                for none
 * @param urgency {@link Urgency#ALERT} for something that needs acting on in
 *                seconds - see FcmPushChannel for what that changes
 */
public record OutboundMessage(String title, String body, String link, String tag, Urgency urgency, String englishSms) {

    public enum Urgency {
        NORMAL,
        /** Sound, vibration where the platform supports it, stays on screen until dismissed. */
        ALERT
    }

    public OutboundMessage {
        if (link != null && (!link.startsWith("/") || link.startsWith("//"))) {
            throw new IllegalArgumentException("A notification link must be a path inside the app: " + link);
        }
    }

    /** A message with no separate SMS wording - the SMS is built from the title and body. */
    public OutboundMessage(String title, String body, String link, String tag, Urgency urgency) {
        this(title, body, link, tag, urgency, null);
    }

    public static OutboundMessage of(String title, String body, String link) {
        return new OutboundMessage(title, body, link, null, Urgency.NORMAL);
    }

    /**
     * englishSms, when set, is what goes by SMS - the English of a message
     * whose push and inbox copy is in her language. An SMS in India has to
     * match a DLT-registered template, and the registered one is English
     * until Hindi and Telugu templates are added. See NotificationCopy.
     */
    public String smsText() {
        if (englishSms != null) {
            return englishSms;
        }
        return "SheOut: " + title + (body == null || body.isBlank() ? "" : ". " + body);
    }
}
