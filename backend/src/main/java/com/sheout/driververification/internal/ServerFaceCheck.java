package com.sheout.driververification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CompareFacesRequest;
import software.amazon.awssdk.services.rekognition.model.CompareFacesResponse;
import software.amazon.awssdk.services.rekognition.model.Image;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

/**
 * The start-of-shift face comparison, done on the server.
 * <p>
 * The partner's phone already compares her selfie with her verified one,
 * but a phone can be made to report anything, and that check is the main
 * thing standing between a verified account and somebody else riding on
 * it. This repeats the comparison where the phone cannot reach it - AWS
 * Rekognition in Mumbai - and its answer wins.
 * <p>
 * It is optional and bounded. Off unless sheout.face-check.enabled (and AWS
 * credentials are present). Each call costs about $0.001, so a monthly cap
 * (default 4,500 calls, about $4.50) is counted in Redis; past it, or if AWS
 * cannot be reached, it answers "no opinion" and the phone's result stands,
 * exactly as before this existed. It never blocks a shift on its own
 * failure.
 * <p>
 * The images go to AWS only for the comparison and are not stored there.
 * The privacy policy must say so before this is switched on.
 */
@Component
public class ServerFaceCheck {

    private static final Logger log = LoggerFactory.getLogger(ServerFaceCheck.class);
    private static final String COUNTER = "face-check:calls:";

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final String region;
    private final long monthlyCap;
    private volatile RekognitionClient client;
    private volatile String cappedMonth;

    public ServerFaceCheck(StringRedisTemplate redis,
                           @Value("${sheout.face-check.enabled:false}") boolean enabled,
                           @Value("${sheout.face-check.region:ap-south-1}") String region,
                           @Value("${sheout.face-check.monthly-cap:4500}") long monthlyCap) {
        this.redis = redis;
        this.enabled = enabled;
        this.region = region;
        this.monthlyCap = monthlyCap;
        if (enabled) {
            log.info("Server face check on: AWS Rekognition {}, at most {} comparisons a month", region, monthlyCap);
        }
    }

    /**
     * How alike the two faces are, 0-100, or empty for "no opinion" - off,
     * over the month's cap, no face found in either image, or AWS
     * unavailable. Callers treat empty as "keep what you had".
     */
    public Optional<Double> similarity(byte[] reference, byte[] selfie) {
        if (!enabled || reference == null || selfie == null || !withinCap()) {
            return Optional.empty();
        }
        try {
            CompareFacesResponse response = client().compareFaces(CompareFacesRequest.builder()
                    .sourceImage(Image.builder().bytes(SdkBytes.fromByteArray(reference)).build())
                    .targetImage(Image.builder().bytes(SdkBytes.fromByteArray(selfie)).build())
                    // Report even weak matches, so a low score is a score, not silence.
                    .similarityThreshold(0f)
                    .build());
            return Optional.of(response.faceMatches().stream()
                    .mapToDouble(m -> m.similarity() == null ? 0 : m.similarity())
                    .max().orElse(0));
        } catch (software.amazon.awssdk.services.rekognition.model.InvalidParameterException e) {
            // Rekognition's answer when it finds no face in one of the images.
            return Optional.empty();
        } catch (RuntimeException | LinkageError e) {
            // LinkageError too: a library clash (an HTTP client class missing
            // at runtime) is an Error, not an exception, and once escaped
            // here it failed every partner's shift check with a 500 - the
            // one thing this class promises never to do.
            log.warn("Server face check unavailable, keeping the phone's result: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Counts this call against the month; false once the month's cap is spent. */
    private boolean withinCap() {
        String month = YearMonth.now(ZoneId.of("Asia/Kolkata")).toString();
        if (month.equals(cappedMonth)) {
            return false;
        }
        try {
            Long used = redis.opsForValue().increment(COUNTER + month);
            if (used != null && used == 1L) {
                redis.expire(COUNTER + month, Duration.ofDays(40));
            }
            if (used != null && used > monthlyCap) {
                cappedMonth = month;
                log.warn("Server face check reached its cap of {} comparisons for {}; phone results stand until next month",
                        monthlyCap, month);
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            // Without a count the spend is unbounded, so no call.
            return false;
        }
    }

    private RekognitionClient client() {
        RekognitionClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = RekognitionClient.builder().region(Region.of(region)).build();
                    client = c;
                }
            }
        }
        return c;
    }
}
