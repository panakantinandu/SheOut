package com.sheout.ratings.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * How long after a trip somebody may still rate it.
 * <p>
 * A window exists because a rating is a memory, and memories go off. Three
 * days after a ride, what is left is rarely the ride - it is whatever has
 * happened since, and often whatever the person is annoyed about now. A
 * score given then tells an operator less than no score at all, because it
 * arrives looking like evidence.
 * <p>
 * It also closes an obvious lever: with no expiry, a trip from six months
 * ago stays a live threat that either party can use against the other.
 * <p>
 * The deadline is written onto each slot when the trip completes rather than
 * being computed at read time. Changing this setting therefore affects trips
 * that complete from then on, and cannot retroactively reopen a slot
 * somebody was already told had closed, or close one they were told was
 * open.
 */
@Component
public class RatingWindow {

    private static final Logger log = LoggerFactory.getLogger(RatingWindow.class);

    private final Duration window;

    RatingWindow(@Value("${sheout.ratings.window-hours:72}") long windowHours) {
        this.window = Duration.ofHours(windowHours);
        log.info("Ratings can be given for {} hours after a trip completes", windowHours);
    }

    public Instant closesAt(Instant completedAt) {
        return completedAt.plus(window);
    }

    public Duration window() {
        return window;
    }
}
