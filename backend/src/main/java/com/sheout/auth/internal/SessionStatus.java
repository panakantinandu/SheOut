package com.sheout.auth.internal;

/**
 * A session is live or it is over. There is no "expired" here: expiry is the
 * token's own business, and a token that has expired stops working without
 * anything being written down.
 */
enum SessionStatus {
    ACTIVE,
    REVOKED
}
