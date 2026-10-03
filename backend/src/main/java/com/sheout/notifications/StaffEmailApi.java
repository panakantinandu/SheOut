package com.sheout.notifications;

/**
 * Plain email to a member of staff at her work address: an invitation, a
 * second-factor reset.
 * <p>
 * Separate from rider and partner notifications on purpose. Those are logged
 * (notification_log) with what was sent, and an invitation's text contains a
 * link that works as a credential until it is used - it must never be stored
 * or logged. Nothing here records the body.
 */
public interface StaffEmailApi {

    /** Whether this deployment can send email at all (SPRING_MAIL_HOST and MAIL_FROM set). */
    boolean isConfigured();

    /** True when the mail server accepted it. Never throws for a failed send. */
    boolean send(String to, String subject, String body);
}
