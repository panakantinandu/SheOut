package com.sheout.booking.internal;

import com.sheout.booking.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Ends searches that dispatch lost track of, from the one record that
 * cannot lose them: the booking row.
 * <p>
 * Dispatch keeps a search's progress in Redis and reports the end of it with
 * DispatchExhausted, which is what normally moves a booking out of
 * REQUESTED. That report depends on the Redis state still being there when
 * the round is examined, and on Render it often is not: the service sleeps
 * when idle, the round state expires after thirty minutes, and on waking the
 * sweeper finds an expired round with nothing behind it and moves on. The
 * booking stayed REQUESTED for good - production had riders with twenty of
 * them, days old, each listed under Live Track as "Finding a partner".
 * <p>
 * So the booking module checks for itself. Anything still REQUESTED well past
 * the search budget cannot still be searching, and is recorded as
 * NO_DRIVERS_AVAILABLE through the same transition dispatch would have used.
 * That status is the honest one - nobody cancelled - and it counts against
 * nobody's record. It publishes nothing, so a rider is not sent "no partner
 * found" about a ride she asked for last week.
 * <p>
 * The grace period keeps this strictly behind dispatch: a live search always
 * ends itself first, and this only ever sees the ones that were dropped.
 */
@Component
class StaleSearchReaper {

    private static final Logger log = LoggerFactory.getLogger(StaleSearchReaper.class);
    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final Duration staleAfter;

    StaleSearchReaper(
            BookingRepository bookingRepository,
            BookingService bookingService,
            // The same property dispatch reads its budget from, so the two
            // cannot drift apart when it is retuned.
            @Value("${sheout.dispatch.search-timeout-seconds:90}") long searchTimeoutSeconds,
            @Value("${sheout.booking.stale-search-grace-seconds:60}") long graceSeconds) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.staleAfter = Duration.ofSeconds(searchTimeoutSeconds + graceSeconds);
    }

    @Scheduled(
            initialDelayString = "${sheout.booking.stale-search-initial-delay-ms:15000}",
            fixedDelayString = "${sheout.booking.stale-search-interval-ms:60000}")
    public void reap() {
        Instant cutoff = Instant.now().minus(staleAfter);
        List<BookingEntity> stale;
        do {
            stale = bookingRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                    BookingStatus.REQUESTED, cutoff);
            int ended = 0;
            for (BookingEntity booking : stale) {
                // Each in its own transaction, through the state machine: a
                // booking matched or cancelled since the read is refused and
                // left exactly as it is.
                if (bookingService.markNoDriversAvailable(booking.getId()).isSuccess()) {
                    ended++;
                }
            }
            if (ended > 0) {
                log.info("Ended {} search(es) still REQUESTED more than {}s after they began",
                        ended, staleAfter.toSeconds());
            }
            // A refused row would come back on the next read, so only go
            // round again while every row in the batch actually moved.
            if (ended < stale.size()) {
                break;
            }
        } while (stale.size() == BATCH_SIZE);
    }
}
