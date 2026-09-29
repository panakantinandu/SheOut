package com.sheout.notifications.internal;

import com.sheout.auth.AccountRole;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * When a "we miss you" reminder is due, as plain rules - see ReengagementNudger.
 * <p>
 * Shaped the way ride apps treat people who drift away: a short ladder, not
 * a drip. One reminder at about a week away, one at two weeks, one at a
 * month, and then silence - after six weeks she has most likely gone, and
 * more pushes only teach her to turn notifications off, which costs the trip
 * updates that matter. Never two within six days. The moment she opens the
 * app again the ladder starts over, so a regular who skips one week hears
 * one gentle word, not the month-away one.
 * <p>
 * Sent only in daytime hours in India, when a nudge can lead somewhere: a
 * rider between 9 in the morning and 8 at night, a partner between 8 and
 * noon, as her working day starts. Nothing at night.
 */
final class ReengagementPlan {

    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    static final Duration FIRST = Duration.ofDays(7);
    static final Duration SECOND = Duration.ofDays(14);
    static final Duration THIRD = Duration.ofDays(30);
    /** Away longer than this, she is left alone. */
    static final Duration GIVE_UP = Duration.ofDays(45);
    /** Never two reminders closer than this. */
    static final Duration MIN_GAP = Duration.ofDays(6);

    /** The reminder she was last sent, if any. */
    record Previous(int stage, Instant nudgedAt, Instant seenAtWhenNudged) {
    }

    private ReengagementPlan() {
    }

    /** Whether reminders go to this role at this moment. */
    static boolean inSendingHours(AccountRole role, Instant now) {
        int hour = ZonedDateTime.ofInstant(now, INDIA).getHour();
        return switch (role) {
            case CUSTOMER -> hour >= 9 && hour < 20;
            case DRIVER -> hour >= 8 && hour < 12;
            case ADMIN -> false;
        };
    }

    /**
     * The step of the ladder to send now - 1, 2 or 3 - or 0 for none.
     *
     * @param deviceSeen  the last time one of her devices checked in (the app registers on every start)
     * @param lastActive  the newest activity across her live sign-ins; empty when she is signed out everywhere
     * @param previous    the last reminder she was sent
     */
    static int stageDue(Instant now, Instant deviceSeen, Optional<Instant> lastActive, Optional<Previous> previous) {
        // Signed out everywhere: a reminder would open on a sign-in screen. Leave her be.
        if (lastActive.isEmpty()) {
            return 0;
        }
        Instant seen = lastActive.get().isAfter(deviceSeen) ? lastActive.get() : deviceSeen;
        Duration away = Duration.between(seen, now);
        if (away.compareTo(FIRST) < 0 || away.compareTo(GIVE_UP) > 0) {
            return 0;
        }
        int sentThisStretch = 0;
        if (previous.isPresent()) {
            Previous p = previous.get();
            boolean cameBackSince = seen.isAfter(p.seenAtWhenNudged());
            if (!cameBackSince) {
                if (Duration.between(p.nudgedAt(), now).compareTo(MIN_GAP) < 0) {
                    return 0;
                }
                sentThisStretch = p.stage();
            }
        }
        int step = away.compareTo(THIRD) >= 0 ? 3 : away.compareTo(SECOND) >= 0 ? 2 : 1;
        return step > sentThisStretch ? step : 0;
    }

    /** When she was last seen, for recording against the reminder. */
    static Instant seenAt(Instant deviceSeen, Optional<Instant> lastActive) {
        return lastActive.filter(a -> a.isAfter(deviceSeen)).orElse(deviceSeen);
    }

    /**
     * What to say. A partner not yet verified is asked to finish, not to go
     * online - she can't yet, and being told to would only confuse her.
     */
    static String copyKey(AccountRole role, int stage, boolean partnerVerified) {
        if (role == AccountRole.DRIVER) {
            return partnerVerified ? "reengage.partner" + stage : "reengage.partnerVerify";
        }
        return "reengage.rider" + stage;
    }
}
