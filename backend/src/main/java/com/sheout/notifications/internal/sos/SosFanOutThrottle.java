package com.sheout.notifications.internal.sos;

import com.sheout.sharedkernel.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Decides whether one SOS press texts her contacts again. It never decides
 * whether the SOS is accepted - that is always yes.
 * <p>
 * DELIBERATE, AND NOT TO BE TIGHTENED WITHOUT THAT CONVERSATION: this errs
 * toward never getting in the way of a real emergency, and tolerates some
 * spam risk to do it. So:
 * <ul>
 *   <li>Nothing here ever refuses the request, returns a 429, or stops the
 *       alert being recorded. Every press is saved with its location and
 *       shows up on the ops console, however many there are.</li>
 *   <li>The first several presses in a window all text every contact,
 *       unconditionally. Somebody pressing repeatedly because nothing seems
 *       to be happening, or because she has moved, reaches her contacts
 *       each time.</li>
 *   <li>Past that, contacts are still texted - at most once a minute rather
 *       than on every press, each text carrying the newest location.</li>
 *   <li>A press is only ever held back when a text actually reached somebody
 *       in the last minute (SosService checks). If sends are failing, every
 *       press retries.</li>
 * </ul>
 * What it stops is one account turning SOS into an unmetered SMS gun aimed at
 * whatever numbers it saved as contacts, hundreds of texts a minute billed to
 * us. Bounded, it is a handful plus one a minute.
 * <p>
 * Fails open like every other limiter here: if Redis is down, contacts are
 * texted.
 */
@Component
class SosFanOutThrottle {

    static final Duration SPACING = Duration.ofMinutes(1);

    private final RateLimiter rateLimiter;
    private final int burst;
    private final Duration burstWindow;

    SosFanOutThrottle(RateLimiter rateLimiter,
                      @Value("${sheout.rate-limit.sos-burst:5}") int burst,
                      @Value("${sheout.rate-limit.sos-burst-window-minutes:10}") long burstWindowMinutes) {
        this.rateLimiter = rateLimiter;
        this.burst = burst;
        this.burstWindow = Duration.ofMinutes(burstWindowMinutes);
    }

    /** True when this press should text her contacts. */
    boolean shouldTextContacts(UUID customerAccountId) {
        if (rateLimiter.tryConsume("sos-burst:" + customerAccountId, burst, burstWindow).allowed()) {
            return true;
        }
        return rateLimiter.tryConsume("sos-spacing:" + customerAccountId, 1, SPACING).allowed();
    }
}
