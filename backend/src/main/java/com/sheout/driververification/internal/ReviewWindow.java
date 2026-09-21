package com.sheout.driververification.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * One review, as the two moments that bound it.
 * <p>
 * The subtraction is done here rather than in the query: date arithmetic in
 * JPQL is database-specific, and this is a handful of rows either way.
 */
record ReviewWindow(Instant submittedAt, Instant reviewedAt) {

    long seconds() {
        return Duration.between(submittedAt, reviewedAt).toSeconds();
    }
}
