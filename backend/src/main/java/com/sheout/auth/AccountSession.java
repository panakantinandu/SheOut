package com.sheout.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * One of her own live sign-ins, as she sees it in her list of devices.
 * <p>
 * Device is a plain phrase she would recognise ("Android phone"), not the
 * raw user agent: the point is to help her spot one she does not recognise,
 * and a browser string helps nobody do that.
 */
public record AccountSession(
        UUID id,
        String device,
        Instant signedInAt,
        Instant lastActiveAt,
        /** The device asking. Hers to keep - signing it out is signing out. */
        boolean current
) {
}
