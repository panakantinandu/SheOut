package com.sheout.sharedkernel.warmup;

import java.util.List;

/**
 * One module's part of warming a freshly started instance before it takes
 * traffic - see platform.WarmUpRunner.
 * <p>
 * A new JVM is slow the first time each code path runs: classes load, the
 * connection pools fill, Hibernate prepares its statements, the HTTP client
 * to the router opens its first connection. On a small instance a burst of
 * real bookings arriving at that moment all pay for it at once (a 12.7 s p95
 * on staging, against 1.7 s once warm). A module lists here what its first
 * real users would run, so that cost is paid before any of them arrive.
 * <p>
 * The rule that makes this safe in production: <b>nothing a warm-up does may
 * be seen by anybody.</b> Read with made-up ids, compute with fixed points,
 * write only inside a transaction that is rolled back, and publish no events
 * - a real booking would reach dispatch, which writes offers to Redis inside
 * the same transaction, and a rollback does not take those back.
 */
public interface WarmUp {

    /** For the log line: which module this is. */
    String name();

    /** This module's hot paths, called in-process with synthetic input. */
    default void inProcess() {
    }

    /**
     * Requests through the whole web stack - filters, JSON, validation, the
     * controller and its error handling - sent to this instance itself.
     * Without credentials they are refused inside the controller, after all
     * of that has run; with a synthetic token, after the token and session
     * checks have too. Either way nothing is read or changed.
     */
    default List<Request> requests() {
        return List.of();
    }

    /** One loopback request; {@code bearerToken} null to send none. */
    record Request(String method, String path, String jsonBody, String bearerToken) {

        public static Request get(String path) {
            return new Request("GET", path, null, null);
        }

        public static Request post(String path, String jsonBody) {
            return new Request("POST", path, jsonBody, null);
        }
    }
}
