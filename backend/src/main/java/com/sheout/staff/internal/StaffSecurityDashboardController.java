package com.sheout.staff.internal;

import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The owners' security panel: is anyone trying the door, who is in the
 * console right now, and from which new machines. Owners only (it lists
 * every member of staff's sessions), read straight from the staff tables.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard/security")
@RequiresPermission(Permission.STAFF_MANAGE_OWNER)
class StaffSecurityDashboardController {

    private final JdbcTemplate jdbc;
    private final StaffPasskeys passkeys;

    StaffSecurityDashboardController(JdbcTemplate jdbc, StaffPasskeys passkeys) {
        this.jdbc = jdbc;
        this.passkeys = passkeys;
    }

    record Security(int failedSignIns24h, int lockedNow, int pendingApprovals, List<Device> newDevices7d,
                    List<Online> onlineNow, Integer passkeysMissing) {
    }

    record Device(UUID staffId, String name, String role, Instant firstSeenAt) {
    }

    record Online(UUID staffId, String name, String role, Instant lastActivityAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    ResponseEntity<Security> security() {
        Instant now = Instant.now();
        Timestamp dayAgo = Timestamp.from(now.minus(Duration.ofHours(24)));
        Timestamp weekAgo = Timestamp.from(now.minus(Duration.ofDays(7)));
        Timestamp nowTs = Timestamp.from(now);

        Integer failed = jdbc.queryForObject("select count(*) from staff_audit_events where action in (?, ?) and occurred_at >= ?",
                Integer.class, StaffActions.SIGN_IN_FAILED, StaffActions.SIGN_IN_LOCKED, dayAgo);
        Integer locked = jdbc.queryForObject("select count(*) from staff_members where locked_until > ?", Integer.class, nowTs);
        Integer pending = jdbc.queryForObject("select count(*) from staff_approval_requests where status = 'PENDING' and expires_at > ?",
                Integer.class, nowTs);
        List<Device> devices = jdbc.query("""
                select m.id, m.display_name, m.role, d.first_seen_at from staff_known_devices d
                join staff_members m on m.id = d.staff_id
                where d.first_seen_at >= ? order by d.first_seen_at desc limit 50""",
                (rs, n) -> new Device(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
                weekAgo);
        // Live: not ended, inside both clocks. One row per person, at their latest activity.
        List<Online> online = jdbc.query("""
                select m.id, m.display_name, m.role, max(s.last_activity_at) from staff_sessions s
                join account_sessions a on a.id = s.session_id
                join staff_members m on m.id = s.staff_id
                where a.revoked_at is null and m.status = 'ACTIVE' and s.absolute_expires_at > ?
                  and s.last_activity_at + make_interval(mins => s.idle_timeout_minutes) > ?
                group by m.id, m.display_name, m.role order by 4 desc""",
                (rs, n) -> new Online(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()),
                nowTs, nowTs);
        // Active members whose role needs a passkey and who have none: each is locked out of all but My account.
        List<String> requiredRoles = java.util.Arrays.stream(com.sheout.staff.StaffRole.values()).filter(passkeys::required)
                .map(Enum::name).toList();
        Integer missing = requiredRoles.isEmpty() ? 0 : jdbc.queryForObject(
                "select count(*) from staff_members m where m.status = 'ACTIVE' and m.role = any(?) "
                        + "and not exists (select 1 from staff_passkeys p where p.staff_id = m.id)", Integer.class,
                (Object) requiredRoles.toArray(new String[0]));
        return ResponseEntity.ok(new Security(failed == null ? 0 : failed, locked == null ? 0 : locked,
                pending == null ? 0 : pending, devices, online, missing));
    }
}
