package com.sheout.staff.internal;

import com.sheout.sharedkernel.web.ClientAddressResolver;
import com.sheout.sharedkernel.web.RequestInfo;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes and reads the staff audit log: append-only, each row chained to the
 * one before by a hash. StaffAuditLog is the face other code uses.
 * <p>
 * WHY A HASH CHAIN. The database stops updates and deletes with a trigger,
 * but whoever holds the database owner's password can drop a trigger. The
 * chain is what makes that visible afterwards: each row's hash covers its
 * own content and the previous row's hash, so changing or removing any row
 * breaks every hash after it. {@link #verify()} recomputes the lot; the daily
 * check (StaffAuditChainCheck) runs it and alerts the owners if it fails.
 * <p>
 * ONE WRITER AT A TIME. Appending takes a transaction-scoped advisory lock,
 * so two rows can never both claim the same predecessor - across every
 * instance, since the lock is in Postgres. The volume is staff actions, not
 * rider traffic, so a single queue is cheap.
 * <p>
 * ITS OWN TRANSACTION. A refused request, or one whose work rolls back, is
 * exactly what most needs a record; writing in the caller's transaction would
 * lose it with the rollback.
 * <p>
 * Values are cut to their column sizes and timestamps to microseconds BEFORE
 * hashing, so what is hashed is exactly what Postgres stores and reads back.
 */
@Service
class StaffAuditWriter {

    private static final Logger log = LoggerFactory.getLogger(StaffAuditWriter.class);
    /** Any fixed number; it names the lock, nothing else. */
    private static final long CHAIN_LOCK = 0x5348_4541_5544_4954L;
    static final String GENESIS = "0".repeat(64);

    private final JdbcTemplate jdbc;
    private final ClientAddressResolver clientAddress;

    StaffAuditWriter(JdbcTemplate jdbc, ClientAddressResolver clientAddress) {
        this.jdbc = jdbc;
        this.clientAddress = clientAddress;
    }

    /** One row as stored. */
    record Row(long seq, UUID id, Instant occurredAt, UUID staffId, UUID accountId, String staffRole, String action,
               String permission, String result, String targetType, String targetId, String reason, String detail,
               String ipAddress, String userAgent, UUID sessionId, String requestId, String prevHash, String hash) {

        String canonical() {
            return String.join("|", s(id), Long.toString(micros(occurredAt)), s(staffId), s(accountId), e(staffRole),
                    e(action), e(permission), e(result), e(targetType), e(targetId), e(reason), e(detail),
                    e(ipAddress), e(userAgent), s(sessionId), e(requestId));
        }

        private static String s(UUID value) {
            return value == null ? "" : value.toString();
        }

        private static String e(String value) {
            return value == null ? "" : value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Row append(StaffAudit.Entry entry, StaffPrincipal actor) {
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, CHAIN_LOCK);
        String prev = jdbc.query("select hash from staff_audit_events order by seq desc limit 1",
                rs -> rs.next() ? rs.getString(1) : GENESIS);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Optional<jakarta.servlet.http.HttpServletRequest> request = currentRequest();
        Row unsaved = new Row(0, UUID.randomUUID(), now,
                actor == null ? null : actor.staffId(),
                actor == null ? null : actor.accountId(),
                actor == null ? null : cut(actor.role().name(), 30),
                cut(entry.action(), 120),
                entry.permission() == null ? null : cut(entry.permission().key(), 60),
                entry.result().name(),
                cut(entry.targetType(), 40),
                cut(entry.targetId(), 80),
                cut(entry.reason(), 500),
                cut(entry.detail(), 2000),
                request.map(r -> cut(clientAddress.resolve(r), 64)).orElse(null),
                request.map(r -> cut(r.getHeader("User-Agent"), 200)).orElse(null),
                actor == null ? null : actor.sessionId(),
                cut(RequestInfo.requestId().orElse(null), 64),
                prev, null);
        String hash = StaffTokens.sha256(prev + "|" + unsaved.canonical());
        Long seq = jdbc.queryForObject("""
                insert into staff_audit_events (id, occurred_at, staff_id, account_id, staff_role, action, permission, result,
                    target_type, target_id, reason, detail, ip_address, user_agent, session_id, request_id, prev_hash, hash)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning seq""", Long.class,
                unsaved.id(), Timestamp.from(now), unsaved.staffId(), unsaved.accountId(), unsaved.staffRole(),
                unsaved.action(), unsaved.permission(), unsaved.result(), unsaved.targetType(), unsaved.targetId(),
                unsaved.reason(), unsaved.detail(), unsaved.ipAddress(), unsaved.userAgent(), unsaved.sessionId(),
                unsaved.requestId(), prev, hash);
        return new Row(seq, unsaved.id(), now, unsaved.staffId(), unsaved.accountId(), unsaved.staffRole(),
                unsaved.action(), unsaved.permission(), unsaved.result(), unsaved.targetType(), unsaved.targetId(),
                unsaved.reason(), unsaved.detail(), unsaved.ipAddress(), unsaved.userAgent(), unsaved.sessionId(),
                unsaved.requestId(), prev, hash);
    }

    /** The outcome of walking the chain. brokenAt is the first row whose hash does not hold, if any. */
    record Verification(long rowsChecked, Long brokenAt, String problem) {
        boolean intact() {
            return brokenAt == null;
        }
    }

    /**
     * Recomputes every hash from the first row. A changed row fails its own
     * hash; a deleted row leaves the next one pointing at a hash that is not
     * its predecessor's.
     */
    @Transactional(readOnly = true)
    Verification verify() {
        long[] checked = {0};
        String[] expectedPrev = {GENESIS};
        Long[] broken = {null};
        String[] problem = {null};
        jdbc.query("select * from staff_audit_events order by seq", rs -> {
            if (broken[0] != null) {
                return;
            }
            Row row = map(rs);
            checked[0]++;
            if (!row.prevHash().equals(expectedPrev[0])) {
                broken[0] = row.seq();
                problem[0] = "the row before it is missing or was changed";
                return;
            }
            String recomputed = StaffTokens.sha256(row.prevHash() + "|" + row.canonical());
            if (!recomputed.equals(row.hash())) {
                broken[0] = row.seq();
                problem[0] = "its content no longer matches its hash";
                return;
            }
            expectedPrev[0] = row.hash();
        });
        return new Verification(checked[0], broken[0], problem[0]);
    }

    /** A page of the log, newest first, for the owner's Audit screen. */
    @Transactional(readOnly = true)
    List<Row> search(UUID staffId, String actionPrefix, Instant from, Instant to, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select * from staff_audit_events where 1=1");
        List<Object> args = new ArrayList<>();
        if (staffId != null) {
            sql.append(" and staff_id = ?");
            args.add(staffId);
        }
        if (actionPrefix != null && !actionPrefix.isBlank()) {
            sql.append(" and action like ?");
            args.add(actionPrefix.replace("%", "").replace("_", "\\_") + "%");
        }
        if (from != null) {
            sql.append(" and occurred_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and occurred_at < ?");
            args.add(Timestamp.from(to));
        }
        sql.append(" order by seq desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, n) -> map(rs), args.toArray());
    }

    /** How many times this member of staff did this since then - for burst alerts. */
    @Transactional(readOnly = true)
    long countSince(UUID staffId, String actionPrefix, Instant since) {
        Long n = jdbc.queryForObject("select count(*) from staff_audit_events where staff_id = ? and action like ? and occurred_at >= ?",
                Long.class, staffId, actionPrefix + "%", Timestamp.from(since));
        return n == null ? 0 : n;
    }

    private static Row map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(rs.getLong("seq"), rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("staff_id", UUID.class), rs.getObject("account_id", UUID.class), rs.getString("staff_role"),
                rs.getString("action"), rs.getString("permission"), rs.getString("result"), rs.getString("target_type"),
                rs.getString("target_id"), rs.getString("reason"), rs.getString("detail"), rs.getString("ip_address"),
                rs.getString("user_agent"), rs.getObject("session_id", UUID.class), rs.getString("request_id"),
                rs.getString("prev_hash"), rs.getString("hash"));
    }

    private static long micros(Instant at) {
        return at.getEpochSecond() * 1_000_000L + at.getNano() / 1_000;
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static Optional<jakarta.servlet.http.HttpServletRequest> currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? Optional.of(attrs.getRequest()) : Optional.empty();
    }

    static void logBroken(Verification v) {
        log.error("STAFF AUDIT CHAIN BROKEN at row {} ({}) after {} rows checked - someone changed or deleted audit rows",
                v.brokenAt(), v.problem(), v.rowsChecked());
    }
}
