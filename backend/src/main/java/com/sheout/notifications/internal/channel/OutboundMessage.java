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
public record OutboundMessage(String title, String body, String link, String tag, Urgency urgency) {

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

    public static OutboundMessage of(String title, String body, String link) {
        return new OutboundMessage(title, body, link, null, Urgency.NORMAL);
    }

    public String smsText() {
        return "SheOut: " + title + (body == null || body.isBlank() ? "" : ". " + body);
    }
}
