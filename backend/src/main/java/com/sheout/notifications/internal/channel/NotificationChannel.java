package com.sheout.notifications.internal.channel;

import com.sheout.notifications.internal.NotificationChannelType;
import com.sheout.notifications.internal.SendFailure;
import com.sheout.sharedkernel.Result;

/**
 * The swap point for notification delivery. Nothing that decides WHAT to
 * send knows which provider sends it - NotificationDispatcher asks for a
 * channel by type, same pattern as auth's OtpSender.
 * <p>
 * {@code recipient} is an opaque address: a phone number for SMS
 * (TwilioSmsChannel), an FCM device token for push (FcmPushChannel), an
 * email address for email (SmtpEmailChannel).
 * <p>
 * Implementations never throw for a failed delivery. A provider error, a
 * missing credential and an unreachable host all come back as a SendFailure,
 * because every attempt is recorded and a thrown exception would lose the
 * record of it.
 */
public interface NotificationChannel {

    NotificationChannelType type();

    Result<Void, SendFailure> send(String recipient, OutboundMessage message);
}
