package com.sheout.notifications.internal;

/**
 * A failed send, together with whatever the provider actually said about it.
 * <p>
 * NotificationError alone was not enough to act on. Every failed SMS in this
 * system recorded the single word "PROVIDER_ERROR", because the enum has
 * nowhere to carry detail and the channel discarded Twilio's response at the
 * boundary. The real reason - bad credentials, an unverified sender, a
 * region that is not enabled, a malformed number - only ever reached the
 * server log, which is not readable from the ops console or the API.
 * <p>
 * That turned a specific, fixable problem into an opaque one, and it is why
 * the SMS outage took this long to pin down. The detail now travels with the
 * error so one failed send names its own cause.
 */
public record SendFailure(NotificationError error, String detail) {

    public static SendFailure of(NotificationError error) {
        return new SendFailure(error, null);
    }

    public static SendFailure of(NotificationError error, String detail) {
        return new SendFailure(error, detail);
    }

    /**
     * What gets stored in the log row's failureReason. Kept inside the
     * column's 500 characters, since a provider error body can be long and a
     * truncation failure here would lose the whole row.
     */
    @Override
    public String toString() {
        if (detail == null || detail.isBlank()) {
            return error.name();
        }
        String combined = error.name() + ": " + detail;
        return combined.length() <= 500 ? combined : combined.substring(0, 500);
    }
}
