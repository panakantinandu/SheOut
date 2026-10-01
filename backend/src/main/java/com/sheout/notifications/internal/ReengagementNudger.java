package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.notifications.internal.PushDeviceRepository.LapsedAccount;
import com.sheout.notifications.internal.channel.OutboundMessage;
import com.sheout.users.DriverProfileApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "We miss you": a reminder to a rider or partner who has not opened SheOut
 * for a week or more. The rules - the 7/14/30-day ladder, the daytime hours,
 * starting over when she comes back - are in ReengagementPlan.
 * <p>
 * Who counts as away is read from what the notifications module already
 * holds, the last time one of her devices checked in (both apps register on
 * every start), and confirmed against her sign-in activity from auth, so a
 * rider who used SheOut yesterday on a phone without notifications is not
 * told she has been missed. Only accounts with a device can be reached by
 * push at all, and push is the only channel this uses.
 * <p>
 * In her own language, with the same short list of lines for everyone at the
 * same step. It runs every half hour and sends only inside the hours the
 * plan allows; off entirely with sheout.notifications.reengagement.enabled.
 */
@Component
class ReengagementNudger {

    private static final Logger log = LoggerFactory.getLogger(ReengagementNudger.class);
    private static final int PAGE_SIZE = 200;
    private static final int MAX_PAGES = 10;

    private final PushDeviceRepository devices;
    private final ReengagementNudgeRepository nudges;
    private final AuthApi authApi;
    private final DriverProfileApi drivers;
    private final NotificationCopy copy;
    private final NotificationDispatcher dispatcher;
    private final boolean enabled;
    private final com.sheout.sharedkernel.cluster.ClusterLock lock;

    ReengagementNudger(PushDeviceRepository devices, ReengagementNudgeRepository nudges, AuthApi authApi,
                       DriverProfileApi drivers, NotificationCopy copy, NotificationDispatcher dispatcher,
                       @Value("${sheout.notifications.reengagement.enabled:true}") boolean enabled,
                       com.sheout.sharedkernel.cluster.ClusterLock lock) {
        this.lock = lock;
        this.devices = devices;
        this.nudges = nudges;
        this.authApi = authApi;
        this.drivers = drivers;
        this.copy = copy;
        this.dispatcher = dispatcher;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${sheout.notifications.reengagement.cron:0 5/30 * * * *}", zone = "Asia/Kolkata")
    public void sweep() {
        // Once per run across servers, or every lapsed account is nudged twice.
        if (enabled) {
            lock.runExclusively("reengagement", java.time.Duration.ofMinutes(20), () -> run(Instant.now()));
        }
    }

    /** Sends whatever reminders are due at this moment; returns how many went out. */
    int run(Instant now) {
        List<AccountRole> roles = new ArrayList<>();
        for (AccountRole role : List.of(AccountRole.CUSTOMER, AccountRole.DRIVER)) {
            if (ReengagementPlan.inSendingHours(role, now)) {
                roles.add(role);
            }
        }
        if (roles.isEmpty()) {
            return 0;
        }
        Instant seenBefore = now.minus(ReengagementPlan.FIRST);
        Instant seenAfter = now.minus(ReengagementPlan.GIVE_UP);
        Instant nudgedSince = now.minus(ReengagementPlan.MIN_GAP);

        int sent = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            List<LapsedAccount> batch = devices.findLapsed(roles, seenBefore, seenAfter, nudgedSince, PageRequest.of(page, PAGE_SIZE));
            for (LapsedAccount lapsed : batch) {
                try {
                    if (nudge(lapsed, now)) {
                        sent++;
                    }
                } catch (RuntimeException e) {
                    // One account's trouble never stops the rest.
                    log.warn("Reengagement reminder failed for {}: {}", lapsed.getAccountId(), e.toString());
                }
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
        }
        if (sent > 0) {
            log.info("Sent {} 'we miss you' reminder(s)", sent);
        }
        return sent;
    }

    private boolean nudge(LapsedAccount lapsed, Instant now) {
        Optional<AccountSummary> account = authApi.findAccount(lapsed.getAccountId());
        // Gone, blocked, or the device's role no longer hers: nothing to say.
        if (account.isEmpty() || account.get().blocked() || account.get().role() != lapsed.getAccountRole()) {
            return false;
        }
        Optional<Instant> lastActive = authApi.lastActiveAt(lapsed.getAccountId());
        Optional<ReengagementNudgeEntity> row = nudges.findById(lapsed.getAccountId());
        Optional<ReengagementPlan.Previous> previous = row.map(
                r -> new ReengagementPlan.Previous(r.getStage(), r.getLastNudgedAt(), r.getSeenAtWhenNudged()));

        int stage = ReengagementPlan.stageDue(now, lapsed.getLastSeen(), lastActive, previous);
        if (stage == 0) {
            return false;
        }
        boolean verified = lapsed.getAccountRole() == AccountRole.DRIVER && drivers.isCurrentlyVerified(lapsed.getAccountId());
        String key = ReengagementPlan.copyKey(lapsed.getAccountRole(), stage, verified);
        NotificationCopy.Localized text = copy.render(lapsed.getAccountId(), key, language -> Map.of());

        dispatcher.deliver(lapsed.getAccountId(), NotificationType.REENGAGEMENT,
                // One tag, so a second reminder replaces the first on her phone rather than stacking.
                new OutboundMessage(text.title(), text.body(), "/home", "sheout-reengagement", OutboundMessage.Urgency.NORMAL));

        ReengagementNudgeEntity record = row.orElseGet(() -> new ReengagementNudgeEntity(lapsed.getAccountId()));
        record.record(stage, now, ReengagementPlan.seenAt(lapsed.getLastSeen(), lastActive));
        nudges.save(record);
        return true;
    }
}
