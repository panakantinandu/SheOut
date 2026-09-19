package com.sheout.dispatch.internal;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Blurs a partner's position before it is shown to riders browsing the
 * booking screen.
 * <p>
 * The preview exists to say "there really are partners near you" - it is not
 * the matching data, and anybody signed in as a rider can see it, not only
 * someone on a trip with that partner. So each position is moved 50-100 m in
 * some direction, and nothing that identifies the partner goes with it.
 * <p>
 * THE OFFSET IS STABLE, NOT FRESH. A new random offset on every request looks
 * safer and is not: the preview is polled every few seconds, and averaging a
 * few dozen independently-jittered samples of a partner waiting in one place
 * converges on where she actually is - her home, if she goes online from
 * there. Here the offset is fixed per partner per day (a keyed hash of her id
 * and the date), so repeated polling returns the same blurred point and
 * averaging gains nothing. The key is random per process and never leaves it,
 * so the offset cannot be recomputed from outside.
 */
@Component
class NearbyDriverPreview {

    private static final double MIN_OFFSET_METRES = 50;
    private static final double MAX_OFFSET_METRES = 100;
    private static final double METRES_PER_DEGREE_LAT = 111_320;

    private final byte[] key = new byte[32];

    NearbyDriverPreview() {
        new SecureRandom().nextBytes(key);
    }

    ApproximatePosition blur(UUID driverId, double lat, double lng) {
        ByteBuffer digest = ByteBuffer.wrap(hmac(driverId + "|" + LocalDate.now(ZoneOffset.UTC)));
        double angle = unit(digest.getLong()) * 2 * Math.PI;
        double metres = MIN_OFFSET_METRES + unit(digest.getLong()) * (MAX_OFFSET_METRES - MIN_OFFSET_METRES);
        double dLat = metres * Math.cos(angle) / METRES_PER_DEGREE_LAT;
        double dLng = metres * Math.sin(angle) / (METRES_PER_DEGREE_LAT * Math.cos(Math.toRadians(lat)));
        // Five decimal places is about a metre; the blur is fifty times that,
        // so rounding cannot undo it and the response carries no false precision.
        return new ApproximatePosition(round5(lat + dLat), round5(lng + dLng));
    }

    /** A long's top 53 bits as a number in [0, 1). */
    private static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static double round5(double v) {
        return Math.round(v * 100_000d) / 100_000d;
    }

    private byte[] hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is required by every Java runtime", e);
        }
    }
}
