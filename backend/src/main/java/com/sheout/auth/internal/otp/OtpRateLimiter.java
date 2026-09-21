package com.sheout.auth.internal.otp;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * How often a code may be asked for, and how often one may be guessed.
 * <p>
 * Without this, the OTP endpoint was an SMS cannon. Twelve requests for one
 * number landed in half a second in testing, each one an SMS attempt. That
 * is two separate problems at once: it bills us per message on a paid
 * Twilio account, and it lets anyone use SheOut to repeatedly text a number
 * they do not own. The victim is not even a user.
 * <p>
 * Requesting a code, per phone number, three limits at once: a short
 * cooldown stops the rapid burst, 5 per 15 minutes is the ceiling the
 * security review set, and the hourly cap stops a patient attacker pacing
 * themselves just inside the other two. Checked shortest first, so somebody
 * tapping "resend" during the cooldown does not also spend their hourly
 * allowance on requests that were never going to be sent.
 * <p>
 * Verifying a code, per phone number, 5 per 15 minutes. OtpService already
 * destroys a code after five wrong guesses; this bounds guesses across
 * codes, which that cannot.
 * <p>
 * Keyed on the number, since that is who suffers - an IP cap alone protects
 * the bill and not the person. The per-IP limits in AuthController are a
 * second, looser layer against one client spraying many numbers.
 * <p>
 * The trade-off, stated plainly: anyone who knows a number can spend that
 * number's allowance and delay its owner's login by up to the window. That
 * is inherent to any per-number limit, and it is the lesser harm next to an
 * unmetered one.
 */
@Component
public class OtpRateLimiter {

    private final RateLimiter rateLimiter;
    private final TestPhoneNumbers testNumbers;
    private final Duration cooldown;
    private final int hourlyLimit;
    private final int requestLimit;
    private final int verifyLimit;
    private final Duration window;

    OtpRateLimiter(RateLimiter rateLimiter,
                   TestPhoneNumbers testNumbers,
                   @Value("${sheout.auth.otp-cooldown-seconds:45}") long cooldownSeconds,
                   @Value("${sheout.auth.otp-hourly-limit:6}") int hourlyLimit,
                   @Value("${sheout.rate-limit.otp-request-per-phone:5}") int requestLimit,
                   @Value("${sheout.rate-limit.otp-verify-per-phone:5}") int verifyLimit,
                   @Value("${sheout.rate-limit.otp-window-minutes:15}") long windowMinutes) {
        this.rateLimiter = rateLimiter;
        this.testNumbers = testNumbers;
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
        this.hourlyLimit = hourlyLimit;
        this.requestLimit = requestLimit;
        this.verifyLimit = verifyLimit;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /** Throws TooManyRequestsException when a code may not be sent to this number now. */
    /**
     * The cooldown and the short window are per app, so signing in to one
     * app never makes the other say "please wait". The hourly cap stays per
     * number across both apps: it is what bounds how many texts one number
     * can be sent, and two apps must not double that.
     */
    public void checkRequest(String phoneNumber, AccountRole role) {
        if (testNumbers.isTestNumber(phoneNumber)) {
            // These limits exist because a request means a text to somebody:
            // it bills us, and it lets a stranger be messaged repeatedly. A
            // test number is texted nothing at all, so neither applies - and
            // the limits were making the people testing the app wait an hour
            // between attempts. Guessing is still limited, below: the code
            // for these numbers is fixed, so that is the one that matters.
            return;
        }
        // Its own code: this refusal means a code went out moments ago, so
        // the app takes her to enter it rather than showing an error.
        rateLimiter.tryConsume("otp-request-cooldown:" + role + ":" + phoneNumber, 1, cooldown)
                .orThrow("OTP_RESEND_COOLDOWN", "A code was sent to this number moments ago.");
        rateLimiter.tryConsume("otp-request:" + role + ":" + phoneNumber, requestLimit, window)
                .orThrow("Too many codes requested for this number. Please wait and try again.");
        rateLimiter.tryConsume("otp-request-hourly:" + phoneNumber, hourlyLimit, Duration.ofHours(1))
                .orThrow("Too many codes requested for this number. Please wait and try again.");
    }

    /** Throws TooManyRequestsException when this number has had too many verification attempts. */
    public void checkVerify(String phoneNumber, AccountRole role) {
        rateLimiter.tryConsume("otp-verify:" + role + ":" + phoneNumber, verifyLimit, window)
                .orThrow("Too many attempts for this number. Please wait and try again.");
    }
}
