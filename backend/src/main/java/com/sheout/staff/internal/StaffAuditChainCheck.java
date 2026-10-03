package com.sheout.staff.internal;

import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.staff.StaffAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Every night, recomputes the whole audit chain. A break is an ERROR in the
 * log (so Sentry raises it), an alert to every owner, and its own audit row;
 * an intact chain leaves one quiet row saying how far it was checked.
 * <p>
 * Whole-chain, not incremental: at a staff team's volume it is seconds, and
 * an incremental check could be fooled by someone who edited a row that had
 * already been checked.
 */
@Component
class StaffAuditChainCheck {

    private static final Logger log = LoggerFactory.getLogger(StaffAuditChainCheck.class);

    private final StaffAuditWriter writer;
    private final StaffAuditLog audit;
    private final ClusterLock lock;

    StaffAuditChainCheck(StaffAuditWriter writer, StaffAuditLog audit, ClusterLock lock) {
        this.writer = writer;
        this.audit = audit;
        this.lock = lock;
    }

    @Scheduled(cron = "${sheout.staff.audit-check-cron:0 30 3 * * *}", zone = "Asia/Kolkata")
    void nightly() {
        lock.runExclusively("staff-audit-chain-check", Duration.ofMinutes(30), this::check);
    }

    StaffAuditWriter.Verification check() {
        StaffAuditWriter.Verification result = writer.verify();
        if (result.intact()) {
            log.info("Staff audit chain intact: {} rows", result.rowsChecked());
            audit.recordQuietly(new StaffAudit.Entry(StaffActions.AUDIT_CHAIN_OK, null, StaffAudit.Result.OK, null, null,
                    null, "{\"rows\":" + result.rowsChecked() + "}"), null);
        } else {
            StaffAuditWriter.logBroken(result);
            audit.record(new StaffAudit.Entry(StaffActions.AUDIT_CHAIN_BROKEN, null, StaffAudit.Result.FAILED, "AUDIT_ROW",
                    String.valueOf(result.brokenAt()), null, "row " + result.brokenAt() + ": " + result.problem()), null);
        }
        return result;
    }
}
