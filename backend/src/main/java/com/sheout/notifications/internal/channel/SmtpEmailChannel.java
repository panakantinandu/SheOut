package com.sheout.notifications.internal.channel;

import com.sheout.notifications.internal.NotificationChannelType;
import com.sheout.notifications.internal.NotificationError;
import com.sheout.notifications.internal.SendFailure;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.logging.Redact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email over SMTP - any provider that speaks it (Brevo, SES, Gmail with an app
 * password). Plain text only: nothing a notification says needs formatting,
 * and HTML mail is a second rendering surface to get wrong.
 * <p>
 * Inert until SPRING_MAIL_HOST and MAIL_FROM are set: Spring only builds a
 * JavaMailSender when spring.mail.host is, and without one a send fails as
 * NOT_CONFIGURED, recorded like any other failure.
 */
@Component
public class SmtpEmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailChannel.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    public SmtpEmailChannel(ObjectProvider<JavaMailSender> mailSender,
                            @Value("${sheout.notifications.email.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public Result<Void, SendFailure> send(String emailAddress, OutboundMessage message) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || from.isBlank()) {
            return Result.failure(SendFailure.of(NotificationError.NOT_CONFIGURED,
                    "Email is not configured on this deployment (set SPRING_MAIL_HOST and MAIL_FROM)"));
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(emailAddress);
        mail.setSubject("SheOut: " + message.title());
        mail.setText((message.body() == null ? "" : message.body() + "\n\n")
                + "Open the SheOut app for details.\n\n"
                + "You are receiving this because it is the email address on your SheOut profile.");
        try {
            sender.send(mail);
            return Result.success(null);
        } catch (MailException e) {
            log.error("Email send to {} failed - {}", Redact.email(emailAddress), e.getMessage());
            return Result.failure(SendFailure.of(NotificationError.PROVIDER_ERROR, "SMTP: " + e.getMessage()));
        }
    }
}
