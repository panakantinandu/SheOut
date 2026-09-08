package com.sheout.auth.internal.otp;

/**
 * The swap point for OTP delivery. {@link OtpService} depends only on this
 * interface, never on a specific provider, so changing providers is a
 * matter of registering a different bean - no change to the request/verify
 * workflow.
 * <p>
 * NOTE on Firebase: Firebase does not have a server-side "send an SMS
 * code" API compatible with this interface. Firebase Phone Auth is a
 * client-driven flow - the mobile/web app talks to Firebase directly to
 * get a verified ID token, and a backend only ever verifies that token
 * (via the Firebase Admin SDK), it never asks Firebase to send a code on
 * its behalf. That's a different integration shape from this
 * generate-code-then-deliver-it model (which MSG91 and Twilio both support
 * directly). See the README for the flagged decision - only a
 * {@link ConsoleOtpSender} is implemented today; wiring MSG91 or Twilio
 * behind this interface is a drop-in follow-up, wiring true Firebase Phone
 * Auth is not (it would change this module's shape).
 */
public interface OtpSender {

    void send(String phoneNumber, String code);
}
