package com.sheout.staff.internal;

import com.sheout.staff.StaffRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Every staff setting in one place, read from sheout.staff.* (application.yml
 * names the environment variables). See README "Staff accounts and roles"
 * before changing them.
 * <p>
 * SESSION LENGTHS ARE PER ROLE. The founder's choice (2026-10-03): 30 minutes
 * idle and 8 hours in all for most roles; the night-shift safety desk 60 and
 * 12, because a responder watching a quiet SOS screen is working, not away;
 * owners 15 idle and 8 hours, because an owner's forgotten tab opens
 * everything. A role without its own setting gets the default.
 */
@Component
class StaffSettings {

    /** Where the console is, for links in invitation emails. Ends with a slash. */
    final String consoleUrl;
    final Duration inviteValidFor;
    final boolean cookieSecure;
    final int maxFailedLogins;
    final Duration lockFor;
    final int loginsPerAddress;
    /** An auditor invited or moved to AUDITOR with no end date gets this many days. */
    final Duration auditorDefaultAccess;
    /** How long a re-entered authenticator code covers sensitive actions. */
    final Duration stepUpWindow;
    /** How stale last-activity may get before a request writes it again. */
    final Duration activityWriteEvery = Duration.ofSeconds(20);

    private final Map<StaffRole, Duration> idle = new EnumMap<>(StaffRole.class);
    private final Map<StaffRole, Duration> absolute = new EnumMap<>(StaffRole.class);

    StaffSettings(@Value("${sheout.staff.console-url:http://localhost:8080/admin/}") String consoleUrl,
                  @Value("${sheout.staff.session.idle-minutes:30}") long idleMinutes,
                  @Value("${sheout.staff.session.absolute-hours:8}") long absoluteHours,
                  @Value("${sheout.staff.session.owner-idle-minutes:15}") long ownerIdleMinutes,
                  @Value("${sheout.staff.session.owner-absolute-hours:8}") long ownerAbsoluteHours,
                  @Value("${sheout.staff.session.safety-idle-minutes:60}") long safetyIdleMinutes,
                  @Value("${sheout.staff.session.safety-absolute-hours:12}") long safetyAbsoluteHours,
                  @Value("${sheout.staff.invite-hours:24}") long inviteHours,
                  @Value("${sheout.staff.cookie-secure:true}") boolean cookieSecure,
                  @Value("${sheout.staff.login.max-failures:5}") int maxFailedLogins,
                  @Value("${sheout.staff.login.lock-minutes:15}") long lockMinutes,
                  @Value("${sheout.staff.login.per-address:20}") int loginsPerAddress,
                  @Value("${sheout.staff.auditor-default-days:30}") long auditorDefaultDays,
                  @Value("${sheout.staff.step-up-minutes:5}") long stepUpMinutes) {
        this.stepUpWindow = minutes(stepUpMinutes);
        this.consoleUrl = consoleUrl.endsWith("/") ? consoleUrl : consoleUrl + "/";
        for (StaffRole role : StaffRole.values()) {
            idle.put(role, minutes(idleMinutes));
            absolute.put(role, hours(absoluteHours));
        }
        idle.put(StaffRole.OWNER, minutes(ownerIdleMinutes));
        absolute.put(StaffRole.OWNER, hours(ownerAbsoluteHours));
        idle.put(StaffRole.SAFETY_RESPONDER, minutes(safetyIdleMinutes));
        absolute.put(StaffRole.SAFETY_RESPONDER, hours(safetyAbsoluteHours));
        this.inviteValidFor = Duration.ofHours(inviteHours);
        this.cookieSecure = cookieSecure;
        this.maxFailedLogins = maxFailedLogins;
        this.lockFor = Duration.ofMinutes(lockMinutes);
        this.loginsPerAddress = loginsPerAddress;
        if (auditorDefaultDays < 1) {
            throw new IllegalStateException("STAFF_AUDITOR_DEFAULT_DAYS must be at least 1");
        }
        this.auditorDefaultAccess = Duration.ofDays(auditorDefaultDays);
    }

    Duration idleTimeoutFor(StaffRole role) {
        return idle.get(role);
    }

    Duration absoluteLimitFor(StaffRole role) {
        return absolute.get(role);
    }

    private static Duration minutes(long value) {
        if (value < 1) {
            throw new IllegalStateException("A staff idle timeout must be at least one minute");
        }
        return Duration.ofMinutes(value);
    }

    private static Duration hours(long value) {
        if (value < 1) {
            throw new IllegalStateException("A staff session limit must be at least one hour");
        }
        return Duration.ofHours(value);
    }
}
