package com.sheout.staff.internal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The browsers each member of staff has signed in from, as a hash of the
 * browser's own description (its User-Agent). A sign-in from one not seen
 * before is told to her and to every owner: if it was not her, that is how
 * anyone finds out.
 * <p>
 * Deliberately coarse. A User-Agent is not a secret and can be copied; this
 * is a tripwire for the ordinary case - a stolen password used on another
 * machine - not a defence on its own.
 */
@Component
class StaffKnownDevices {

    private final JdbcTemplate jdbc;

    StaffKnownDevices(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String fingerprint(String userAgent) {
        return StaffTokens.sha256(userAgent == null ? "" : userAgent.trim());
    }

    /**
     * Remembers this browser, and says whether it is new for someone who
     * already had others. Her very first browser (the one she joined from) is
     * not "new": there is nothing to compare it with.
     */
    @Transactional
    boolean rememberAndCheckNew(UUID staffId, String userAgent) {
        String hash = fingerprint(userAgent);
        Integer known = jdbc.queryForObject("select count(*) from staff_known_devices where staff_id = ?", Integer.class, staffId);
        int updated = jdbc.update("update staff_known_devices set last_seen_at = now() where staff_id = ? and device_hash = ?",
                staffId, hash);
        if (updated > 0) {
            return false;
        }
        jdbc.update("insert into staff_known_devices (staff_id, device_hash, first_seen_at, last_seen_at) values (?, ?, now(), now())"
                + " on conflict do nothing", staffId, hash);
        return known != null && known > 0;
    }

    /** A rough "Chrome on Windows" for the alert, from the same description. */
    static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "an unnamed browser";
        }
        String ua = userAgent;
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("Chrome/") ? "Chrome" : ua.contains("Safari/") ? "Safari" : "a browser";
        String os = ua.contains("Windows") ? "Windows" : ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS" : ua.contains("Mac OS") ? "macOS"
                : ua.contains("Linux") ? "Linux" : "an unknown system";
        return browser + " on " + os;
    }
}
