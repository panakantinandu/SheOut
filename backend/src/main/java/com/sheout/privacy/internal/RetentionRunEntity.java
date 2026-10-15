package com.sheout.privacy.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One retention task's run: what it matched, and whether it only counted. Written once, never changed. */
@Entity
@Immutable
@Table(name = "retention_runs")
public class RetentionRunEntity extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String task;

    @Column(nullable = false)
    private boolean dryRun;

    @Column(nullable = false)
    private int keepDays;

    @Column(nullable = false)
    private Instant cutoff;

    @Column(nullable = false)
    private int matched;

    @Column(length = 500)
    private String error;

    protected RetentionRunEntity() {
    }

    RetentionRunEntity(String task, boolean dryRun, int keepDays, Instant cutoff, int matched, String error) {
        this.task = task;
        this.dryRun = dryRun;
        this.keepDays = keepDays;
        this.cutoff = cutoff;
        this.matched = matched;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }

    public String getTask() {
        return task;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public int getKeepDays() {
        return keepDays;
    }

    public Instant getCutoff() {
        return cutoff;
    }

    public int getMatched() {
        return matched;
    }

    public String getError() {
        return error;
    }
}
