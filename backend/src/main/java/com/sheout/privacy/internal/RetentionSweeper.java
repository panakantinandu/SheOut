package com.sheout.privacy.internal;

import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.sharedkernel.retention.RetentionTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs every module's RetentionTask once a day and writes down what each
 * removed.
 * <p>
 * OFF BY DEFAULT, AND A DRY RUN BY DEFAULT. With RETENTION_ENABLED=false
 * nothing runs at all. Turned on, it starts as a dry run
 * (RETENTION_DRY_RUN=true): each task only counts what it would remove, and
 * the count goes into retention_runs, so the numbers can be checked before
 * anything is deleted. Deleting needs RETENTION_DRY_RUN=false, a separate
 * and deliberate step.
 * <p>
 * Each task's period is sheout.retention.days.NAME. A task with no period
 * configured is skipped, so a module can ship its task before the business
 * has agreed how long to keep its data.
 */
@Service
class RetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(RetentionSweeper.class);

    private final List<RetentionTask> tasks;
    private final RetentionRunRepository runs;
    private final Environment env;
    private final ClusterLock lock;
    private final boolean enabled;
    private final boolean dryRun;
    private final int batchLimit;

    RetentionSweeper(List<RetentionTask> tasks, RetentionRunRepository runs, Environment env, ClusterLock lock,
                     @Value("${sheout.retention.enabled:false}") boolean enabled,
                     @Value("${sheout.retention.dry-run:true}") boolean dryRun,
                     @Value("${sheout.retention.batch-limit:500}") int batchLimit) {
        this.tasks = tasks;
        this.runs = runs;
        this.env = env;
        this.lock = lock;
        this.enabled = enabled;
        this.dryRun = dryRun;
        this.batchLimit = batchLimit;
    }

    @Scheduled(cron = "${sheout.retention.cron:0 30 3 * * *}", zone = "Asia/Kolkata")
    public void sweep() {
        if (enabled) {
            lock.runExclusively("retention", Duration.ofMinutes(30), () -> run(Instant.now()));
        }
    }

    /** One pass of every task that has a period. Returns what was written to the log. */
    List<RetentionRunEntity> run(Instant now) {
        List<RetentionRunEntity> written = new ArrayList<>();
        for (RetentionTask task : tasks) {
            Integer days = env.getProperty("sheout.retention.days." + task.name(), Integer.class);
            if (days == null || days <= 0) {
                continue;
            }
            Instant cutoff = now.minus(Duration.ofDays(days));
            int matched = 0;
            String error = null;
            try {
                matched = task.sweep(cutoff, dryRun, batchLimit);
            } catch (RuntimeException e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.error("Retention task {} failed", task.name(), e);
            }
            written.add(runs.save(new RetentionRunEntity(task.name(), dryRun, days, cutoff, matched, error)));
            log.info("Retention {} ({} days{}): {} {}", task.name(), days, dryRun ? ", dry run" : "", matched,
                    dryRun ? "would be removed" : "removed");
        }
        return written;
    }
}
