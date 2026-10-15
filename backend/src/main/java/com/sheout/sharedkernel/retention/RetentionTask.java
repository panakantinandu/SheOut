package com.sheout.sharedkernel.retention;

import java.time.Instant;

/**
 * One kind of personal data that is removed once it is old enough - chat
 * messages, shift-check photos, images of rejected documents.
 * <p>
 * Each module implements this for its own tables, so the deletion rules stay
 * with the data they govern; the privacy module's RetentionSweeper decides
 * when the tasks run, how old is old enough (sheout.retention.days.NAME),
 * whether it is a dry run, and writes down what happened. A task that is not
 * given a period does not run.
 */
public interface RetentionTask {

    /** The task's name in configuration and in the retention log, e.g. "chat-messages". */
    String name();

    /**
     * Removes up to {@code limit} items created (or settled) before
     * {@code cutoff}, or only counts them when {@code dryRun}. Returns how
     * many were matched - removed, unless it was a dry run. Never touches an
     * item that is still needed, such as one under an open dispute.
     */
    int sweep(Instant cutoff, boolean dryRun, int limit);
}
