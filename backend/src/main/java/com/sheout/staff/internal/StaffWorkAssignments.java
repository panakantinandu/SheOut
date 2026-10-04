package com.sheout.staff.internal;

import com.sheout.staff.StaffAudit;
import com.sheout.staff.WorkAssignments;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who holds which piece of work (staff_work_assignments). Taking is a single
 * insert that loses quietly to whoever got there first, so two agents can
 * never both hold a partner.
 */
@Service
class StaffWorkAssignments implements WorkAssignments {

    private final JdbcTemplate jdbc;
    private final StaffAuditLog audit;
    private final Duration softLockFor;

    StaffWorkAssignments(JdbcTemplate jdbc, StaffAuditLog audit,
                         @Value("${sheout.staff.soft-lock-hours:12}") long softLockHours) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.softLockFor = Duration.ofHours(softLockHours);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> holder(Kind kind, UUID subjectId) {
        List<UUID> found = jdbc.query("select staff_account_id from staff_work_assignments where kind = ? and subject_id = ?",
                (rs, n) -> rs.getObject(1, UUID.class), kind.name(), subjectId);
        return found.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> heldBy(Kind kind, UUID staffAccountId) {
        return new HashSet<>(jdbc.query("select subject_id from staff_work_assignments where kind = ? and staff_account_id = ?",
                (rs, n) -> rs.getObject(1, UUID.class), kind.name(), staffAccountId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> holders(Kind kind) {
        Map<UUID, UUID> out = new HashMap<>();
        jdbc.query("select subject_id, staff_account_id from staff_work_assignments where kind = ?",
                rs -> {
                    out.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
                }, kind.name());
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Held> held(Kind kind) {
        Map<UUID, Held> out = new HashMap<>();
        jdbc.query("select subject_id, staff_account_id, assigned_at from staff_work_assignments where kind = ?",
                rs -> {
                    out.put(rs.getObject(1, UUID.class), new Held(rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant()));
                }, kind.name());
        return out;
    }

    @Override
    @Transactional
    public void assign(Kind kind, UUID subjectId, UUID staffAccountId) {
        UUID before = holder(kind, subjectId).orElse(null);
        jdbc.update("insert into staff_work_assignments (kind, subject_id, staff_account_id, assigned_at) values (?, ?, ?, ?)"
                + " on conflict (kind, subject_id) do update set staff_account_id = excluded.staff_account_id,"
                + " assigned_at = excluded.assigned_at", kind.name(), subjectId, staffAccountId, Timestamp.from(Instant.now()));
        audit.record(new StaffAudit.Entry(StaffActions.WORK_ASSIGN + "." + kind.name().toLowerCase(), null, StaffAudit.Result.OK,
                kind.name(), subjectId.toString(), null,
                "{\"to\":\"" + staffAccountId + "\"" + (before == null ? "" : ",\"from\":\"" + before + "\"") + "}"));
    }

    /**
     * Soft locks on SOS, trip alerts and shops are for "who is on it right
     * now"; one left behind when the alert was resolved elsewhere would
     * count against its holder on the managers' backlog. Cleared after a
     * shift. A verification hold stays until it is given back.
     */
    @Scheduled(cron = "${sheout.staff.soft-lock-sweep-cron:0 17 * * * *}")
    @Transactional
    public void sweepSoftLocks() {
        jdbc.update("delete from staff_work_assignments where kind <> 'VERIFICATION' and assigned_at < ?",
                Timestamp.from(Instant.now().minus(softLockFor)));
    }

    @Override
    @Transactional
    public boolean take(Kind kind, UUID subjectId, UUID staffAccountId) {
        int inserted = jdbc.update("insert into staff_work_assignments (kind, subject_id, staff_account_id, assigned_at)"
                + " values (?, ?, ?, ?) on conflict do nothing", kind.name(), subjectId, staffAccountId, Timestamp.from(Instant.now()));
        boolean mine = inserted == 1 || holder(kind, subjectId).map(staffAccountId::equals).orElse(false);
        if (inserted == 1) {
            audit.record(StaffAudit.Entry.ok(StaffActions.WORK_TAKE + "." + kind.name().toLowerCase(), null,
                    kind.name(), subjectId.toString()));
        }
        return mine;
    }

    @Override
    @Transactional
    public void release(Kind kind, UUID subjectId) {
        if (jdbc.update("delete from staff_work_assignments where kind = ? and subject_id = ?", kind.name(), subjectId) > 0) {
            audit.record(StaffAudit.Entry.ok(StaffActions.WORK_RELEASE + "." + kind.name().toLowerCase(), null,
                    kind.name(), subjectId.toString()));
        }
    }
}
