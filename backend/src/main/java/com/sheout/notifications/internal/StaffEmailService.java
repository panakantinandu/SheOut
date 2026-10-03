package com.sheout.notifications.internal;

import com.sheout.notifications.StaffEmailApi;
import com.sheout.sharedkernel.logging.Redact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * The same SMTP server as every other email (see SmtpEmailChannel), without
 * the notification log and without the footer that points riders at the app.
 * See StaffEmailApi for why nothing about the message is recorded.
 */
@Service
class StaffEmailService implements StaffEmailApi {

    private static final Logger log = LoggerFactory.getLogger(StaffEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;

    StaffEmailService(ObjectProvider<JavaMailSender> mailSender,
                      @Value("${sheout.notifications.email.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public boolean isConfigured() {
        return mailSender.getIfAvailable() != null && !from.isBlank();
    }

    @Override
    public boolean send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || from.isBlank()) {
            return false;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(to);
        mail.setSubject(subject);
        mail.setText(body);
        try {
            sender.send(mail);
            return true;
        } catch (MailException e) {
            // The exception's message can carry the recipient, never the body.
            log.error("Staff email to {} failed - {}", Redact.email(to), e.getClass().getSimpleName());
            return false;
        }
    }
}
