package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.notifications.Announcement;
import com.sheout.notifications.AnnouncementApi;
import com.sheout.notifications.AnnouncementAudience;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.notifications.internal.channel.FcmPushChannel;
import com.sheout.sharedkernel.Result;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Sending one message to everybody, and writing down what went out.
 * <p>
 * PUSH IS ONE CALL PER AUDIENCE, NOT ONE PER PHONE. Every device subscribes
 * to its app's announcements topic when it registers (see PushDeviceService),
 * so a broadcast is a single request that FCM fans out. Looping over stored
 * tokens would be thousands of requests, minutes of wall time, and a list of
 * dead tokens to prune afterwards - for a message that is identical for
 * everyone.
 * <p>
 * EMAIL IS A LOOP, BECAUSE EMAIL IS. It goes out a page at a time with a
 * pause between batches, so a free mail account's per-minute limit is not the
 * thing that decides whether the second half of the city hears from us.
 * Failures are counted, not thrown: one bad address must not stop the rest.
 * <p>
 * ADDRESSES COME FROM TWO PLACES. A profile holds the address she typed in;
 * an account holds the one she signed in with through Google, and for most
 * people that is the only one on file. Reading profiles alone looked like it
 * worked and quietly reached nobody - the first real broadcast went to one
 * person out of eleven. Both are read and deduplicated, case-insensitively,
 * so somebody with the same address in both places is mailed once.
 * <p>
 * SMS IS OFF UNLESS ASKED FOR. Every message must match a DLT-approved
 * template, and each one is charged. It is here for a safety advisory - not
 * for news, an offer, or anything that can wait for the app to be opened.
 */
@Service
class AnnouncementService implements AnnouncementApi {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementService.class);
    private static final int EMAIL_PAGE_SIZE = 50;

    private final AnnouncementRepository announcements;
    private final FcmPushChannel push;
    private final NotificationDispatcher dispatcher;
    private final CustomerProfileApi customerProfiles;
    private final DriverProfileApi driverProfiles;
    private final AuthApi auth;
    private final long emailBatchPauseMs;
    private final int emailMaxRecipients;

    AnnouncementService(AnnouncementRepository announcements,
                        FcmPushChannel push,
                        NotificationDispatcher dispatcher,
                        CustomerProfileApi customerProfiles,
                        DriverProfileApi driverProfiles,
                        AuthApi auth,
                        // A free Gmail account will not take a thousand mails
                        // in a minute; SendGrid's free tier is 100 a day. Both
                        // are a pause between batches, not a code change.
                        @Value("${sheout.notifications.announcement.email-batch-pause-ms:1000}") long emailBatchPauseMs,
                        @Value("${sheout.notifications.announcement.email-max-recipients:2000}") int emailMaxRecipients) {
        this.announcements = announcements;
        this.push = push;
        this.dispatcher = dispatcher;
        this.customerProfiles = customerProfiles;
        this.driverProfiles = driverProfiles;
        this.auth = auth;
        this.emailBatchPauseMs = emailBatchPauseMs;
        this.emailMaxRecipients = emailMaxRecipients;
    }

    @Override
    @Transactional
    public Announcement broadcast(String title, String body, AnnouncementAudience audience, boolean sendSms, UUID sentBy) {
        AnnouncementEntity record = new AnnouncementEntity(title, body, audience, sendSms, sentBy);
        List<String> notes = new ArrayList<>();

        OutboundMessage message = OutboundMessage.of(title, body, "/notifications");

        // 1. Push: one call per audience.
        int accepted = 0;
        int pushFailed = 0;
        for (AccountRole role : rolesFor(audience)) {
            Result<Void, SendFailure> outcome =
                    push.sendToTopic(PushDeviceService.announcementTopic(role), message);
            if (outcome.isSuccess()) {
                accepted++;
            } else {
                pushFailed++;
                notes.add("push (" + role + "): " + outcome.error().detail());
            }
        }
        record.recordPush(accepted, pushFailed);

        // 2. Email: batched, paced, and never fatal.
        int sent = 0;
        int emailFailed = 0;
        for (AccountRole role : rolesFor(audience)) {
            Counts counts = emailEveryone(role, message);
            sent += counts.sent();
            emailFailed += counts.failed();
            if (counts.note() != null) {
                notes.add(counts.note());
            }
        }
        record.recordEmail(sent, emailFailed);

        // 3. SMS: only when an operator asked for it.
        if (sendSms) {
            notes.add("SMS was requested: send it from the DLT-approved template, to the numbers this reaches, "
                    + "rather than as free text - see TwilioSmsChannel");
            record.recordSms(0, 0);
        }

        record.note(notes.isEmpty() ? null : String.join(" | ", notes));
        announcements.save(record);
        log.info("Announcement '{}' to {}: push accepted {}/{}, emails {} sent {} failed",
                title, audience, accepted, accepted + pushFailed, sent, emailFailed);
        return record.toSummary();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Announcement> history(int limit) {
        return announcements.findAllByOrderByCreatedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
                .stream()
                .map(AnnouncementEntity::toSummary)
                .toList();
    }

    private Counts emailEveryone(AccountRole role, OutboundMessage message) {
        List<String> recipients = addressesFor(role);
        int sent = 0;
        int failed = 0;
        String note = null;
        for (int i = 0; i < recipients.size(); i++) {
            Result<Void, SendFailure> outcome = dispatcher.sendEmail(recipients.get(i), message);
            if (outcome.isSuccess()) {
                sent++;
            } else {
                failed++;
                if (note == null) {
                    // The first reason is the useful one: if mail is not
                    // configured, every address fails for that reason.
                    note = "email (" + role + "): " + outcome.error().detail();
                }
            }
            // Pace it in batches, not per message - see EMAIL_PAGE_SIZE.
            if ((i + 1) % EMAIL_PAGE_SIZE == 0) {
                pause();
            }
        }
        return new Counts(sent, failed, note);
    }

    /**
     * Everyone in this role who can be emailed at all, each address once.
     * <p>
     * Keyed on the lowercased address so the same person is not mailed twice
     * for spelling it differently in the two places it can be stored; the
     * address that is actually sent to is the one as written.
     */
    private List<String> addressesFor(AccountRole role) {
        BiFunction<Integer, Integer, List<String>> profilePage = role == AccountRole.DRIVER
                ? driverProfiles::findEmailAddresses
                : customerProfiles::findEmailAddresses;
        Map<String, String> unique = new LinkedHashMap<>();
        collect(unique, profilePage);
        collect(unique, (page, size) -> auth.findEmailAddresses(role, page, size));
        return List.copyOf(unique.values());
    }

    private void collect(Map<String, String> into, BiFunction<Integer, Integer, List<String>> pageOf) {
        for (int page = 0; into.size() < emailMaxRecipients; page++) {
            List<String> batch = pageOf.apply(page, EMAIL_PAGE_SIZE);
            if (batch.isEmpty()) {
                return;
            }
            for (String address : batch) {
                if (address == null || address.isBlank()) {
                    continue;
                }
                into.putIfAbsent(address.trim().toLowerCase(java.util.Locale.ROOT), address.trim());
                if (into.size() >= emailMaxRecipients) {
                    return;
                }
            }
        }
    }

    /** Paces the mailing. Interrupted means the app is stopping, so stop sending. */
    private void pause() {
        if (emailBatchPauseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(emailBatchPauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<AccountRole> rolesFor(AnnouncementAudience audience) {
        return switch (audience) {
            case ALL_CUSTOMERS -> List.of(AccountRole.CUSTOMER);
            case ALL_DRIVERS -> List.of(AccountRole.DRIVER);
            case BOTH -> List.of(AccountRole.CUSTOMER, AccountRole.DRIVER);
        };
    }

    private record Counts(int sent, int failed, String note) {
    }
}
