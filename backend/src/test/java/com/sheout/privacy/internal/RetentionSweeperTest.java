package com.sheout.privacy.internal;

import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.sharedkernel.retention.RetentionTask;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Retention is off unless switched on, a dry run unless told otherwise,
 * runs only tasks that have a period, and writes down every run - including
 * one that failed.
 */
class RetentionSweeperTest {

    private static final Instant NOW = Instant.parse("2026-10-14T22:00:00Z");

    private final RetentionRunRepository runs = mock(RetentionRunRepository.class);
    private final ClusterLock lock = mock(ClusterLock.class);
    private final RetentionTask chat = task("chat-messages");
    private final RetentionTask photos = task("shift-photos");
    private final RetentionTask unconfigured = task("something-new");
    private final MockEnvironment env = new MockEnvironment()
            .withProperty("sheout.retention.days.chat-messages", "90")
            .withProperty("sheout.retention.days.shift-photos", "30");

    private static RetentionTask task(String name) {
        RetentionTask t = mock(RetentionTask.class);
        when(t.name()).thenReturn(name);
        return t;
    }

    private RetentionSweeper sweeper(boolean enabled, boolean dryRun) {
        when(runs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return new RetentionSweeper(List.of(chat, photos, unconfigured), runs, env, lock, enabled, dryRun, 500);
    }

    @Test
    void offByDefaultNothingRuns() {
        sweeper(false, true).sweep();

        verifyNoInteractions(lock);
        verify(chat, never()).sweep(any(), anyBoolean(), anyInt());
    }

    @Test
    void aDryRunOnlyCountsAndRecordsTheCount() {
        when(chat.sweep(any(), eq(true), anyInt())).thenReturn(12);

        List<RetentionRunEntity> written = sweeper(true, true).run(NOW);

        verify(chat).sweep(NOW.minus(Duration.ofDays(90)), true, 500);
        verify(photos).sweep(NOW.minus(Duration.ofDays(30)), true, 500);
        verify(chat, never()).sweep(any(), eq(false), anyInt());
        assertThat(written).extracting(RetentionRunEntity::getTask).containsExactly("chat-messages", "shift-photos");
        assertThat(written.get(0).isDryRun()).isTrue();
        assertThat(written.get(0).getMatched()).isEqualTo(12);
        assertThat(written.get(0).getKeepDays()).isEqualTo(90);
    }

    @Test
    void aTaskWithNoPeriodIsSkipped() {
        sweeper(true, true).run(NOW);

        verify(unconfigured, never()).sweep(any(), anyBoolean(), anyInt());
    }

    @Test
    void aFailingTaskIsRecordedAndTheOthersStillRun() {
        when(chat.sweep(any(), anyBoolean(), anyInt())).thenThrow(new IllegalStateException("storage unavailable"));

        List<RetentionRunEntity> written = sweeper(true, false).run(NOW);

        assertThat(written.get(0).getError()).contains("storage unavailable");
        verify(photos).sweep(any(), eq(false), anyInt());
    }
}
