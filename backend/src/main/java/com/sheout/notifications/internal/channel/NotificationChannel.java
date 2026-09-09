package com.sheout.notifications.internal.channel;

import com.sheout.notifications.internal.NotificationError;
import com.sheout.sharedkernel.Result;

/**
 * The swap point for notification delivery - NotificationEventListeners and
 * SosService depend only on this interface, never on a specific provider,
 * same pattern as auth's OtpSender. {@code recipient} is a phone number for
 * an SMS implementation, or a device token for a push implementation - an
 * opaque address as far as this interface is concerned.
 * <p>
 * ONLY AN SMS IMPLEMENTATION EXISTS (see TwilioSmsChannel) - there is no
 * FcmPushChannel yet, and that's a real gap, not an oversight: nowhere in
 * this codebase does a client ever register a device/FCM token anywhere on
 * the backend, so there is no recipient address a push implementation could
 * actually send to. Adding one is a drop-in follow-up behind this same
 * interface (matching sheout.firebase.project-id/credentials-json, already
 * reserved in application.yml) once device-token registration exists - it
 * would not require touching NotificationEventListeners or SosService.
 */
public interface NotificationChannel {

    Result<Void, NotificationError> send(String recipient, String message);
}
