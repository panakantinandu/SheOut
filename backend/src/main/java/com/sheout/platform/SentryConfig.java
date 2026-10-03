package com.sheout.platform;

import com.sheout.sharedkernel.logging.Redact;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.Message;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What leaves this service when something breaks.
 * <p>
 * Sentry is configured in application.yml and does nothing at all until
 * SENTRY_DSN is set - no DSN, no client, no network calls. What this class
 * adds is the part that is not configuration: an outgoing event is personal
 * data leaving the building, and the same rule already applied to log lines
 * applies here.
 * <p>
 * Phone numbers are the ones that actually turn up. They arrive in exception
 * messages from providers ("no such recipient +9198...") and in breadcrumbs,
 * and a stack trace pasted into a support thread or read by whoever has a
 * Sentry login is exactly the audience Redact exists to keep them from. The
 * country code and last two digits survive, so two reports about the same
 * number can still be tied together.
 * <p>
 * send-default-pii stays off in application.yml, which keeps request headers,
 * cookies and the client IP out of events; this handles the text.
 * <p>
 * Staff invitation links are scrubbed too. One is written to the log only
 * when email is not set up (staff.internal.OwnerBootstrap), at INFO, which
 * Sentry does not collect - this is the second guard, in case a link ever
 * reaches a message that is.
 */
@Configuration
class SentryConfig {

    @Bean
    SentryOptions.BeforeSendCallback scrubPersonalData() {
        return (event, hint) -> {
            scrubMessage(event);
            scrubInviteLinks(event);
            scrubExceptions(event);
            if (event.getBreadcrumbs() != null) {
                event.getBreadcrumbs().forEach(crumb -> crumb.setMessage(Redact.phoneNumbersIn(crumb.getMessage())));
            }
            return event;
        };
    }

    private static final java.util.regex.Pattern INVITE_TOKEN = java.util.regex.Pattern.compile("#invite=[A-Za-z0-9_-]+");

    static String withoutInviteTokens(String text) {
        return text == null ? null : INVITE_TOKEN.matcher(text).replaceAll("#invite=[redacted]");
    }

    private static void scrubInviteLinks(SentryEvent event) {
        Message message = event.getMessage();
        if (message != null) {
            message.setMessage(withoutInviteTokens(message.getMessage()));
            message.setFormatted(withoutInviteTokens(message.getFormatted()));
            if (message.getParams() != null) {
                message.setParams(message.getParams().stream().map(SentryConfig::withoutInviteTokens).toList());
            }
        }
        if (event.getBreadcrumbs() != null) {
            event.getBreadcrumbs().forEach(crumb -> crumb.setMessage(withoutInviteTokens(crumb.getMessage())));
        }
    }

    private static void scrubMessage(SentryEvent event) {
        Message message = event.getMessage();
        if (message == null) {
            return;
        }
        message.setMessage(Redact.phoneNumbersIn(message.getMessage()));
        message.setFormatted(Redact.phoneNumbersIn(message.getFormatted()));
        if (message.getParams() != null) {
            message.setParams(message.getParams().stream().map(Redact::phoneNumbersIn).toList());
        }
    }

    private static void scrubExceptions(SentryEvent event) {
        if (event.getExceptions() == null) {
            return;
        }
        for (SentryException exception : event.getExceptions()) {
            exception.setValue(Redact.phoneNumbersIn(exception.getValue()));
        }
    }
}
