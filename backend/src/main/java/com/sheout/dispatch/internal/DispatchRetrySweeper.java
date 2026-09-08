package com.sheout.dispatch.internal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic sweep for offer rounds whose accept window has passed with no
 * winner, rather than a one-off scheduled task per booking.
 * <p>
 * ASSUMPTION FLAGGED: chose a recurring sweep over `TaskScheduler.schedule`-
 * ing a single delayed callback per round - a one-off in-memory scheduled
 * task would simply vanish if the app restarted mid-window (this booking
 * would then be stuck REQUESTED forever with no retry), whereas a sweep
 * re-derives what's due from Redis on every tick and survives a restart.
 * Given this module was explicitly called out as a likely first candidate
 * to extract into its own service, that robustness seemed worth the
 * (small, configurable) polling interval. Poll interval and both timeout
 * defaults (offer window, radius/retry knobs - see DispatchService) are
 * guesses, not given in the spec beyond "e.g. 15 seconds" for the window.
 */
@Component
public class DispatchRetrySweeper {

    private final DispatchService dispatchService;

    public DispatchRetrySweeper(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${sheout.dispatch.sweep-interval-ms:2000}")
    public void sweep() {
        dispatchService.sweepExpiredRounds();
    }
}
