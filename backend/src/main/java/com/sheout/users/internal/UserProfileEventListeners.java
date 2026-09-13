package com.sheout.users.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingMatched;
import com.sheout.booking.BookingRequested;
import com.sheout.driververification.AccountVerified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

@Component
class UserProfileEventListeners {

    private static final Logger log = LoggerFactory.getLogger(UserProfileEventListeners.class);

    private final CustomerProfileRepository customerProfileRepository;
    private final DriverProfileRepository driverProfileRepository;
    private final CancellationPolicy cancellationPolicy;

    UserProfileEventListeners(CustomerProfileRepository customerProfileRepository,
                               DriverProfileRepository driverProfileRepository,
                               CancellationPolicy cancellationPolicy) {
        this.customerProfileRepository = customerProfileRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.cancellationPolicy = cancellationPolicy;
    }

    /**
     * Creates the profile row every account needs one of, so a profile
     * always exists by the time a client can call any self-service
     * endpoint here - auth only issues a token after this listener has run
     * (same transaction as AuthService.verifyOtp/verifyGoogleSignIn).
     * <p>
     * Name is left empty for a phone signup (not known at signup time -
     * the client fills it in later via the self-service PUT, see
     * CustomerProfileController), but pre-filled for a Google signup since
     * the ID token's "name" claim already gives it - event.name() is null
     * in the phone case, non-null (usually) in the Google case.
     */
    @EventListener
    @Transactional
    public void onAccountRegistered(AccountRegistered event) {
        switch (event.role()) {
            case CUSTOMER -> {
                CustomerProfileEntity profile = new CustomerProfileEntity(event.accountId());
                if (event.name() != null) profile.setName(event.name());
                customerProfileRepository.save(profile);
            }
            case DRIVER -> {
                DriverProfileEntity profile = new DriverProfileEntity(event.accountId());
                if (event.name() != null) profile.setName(event.name());
                driverProfileRepository.save(profile);
            }
            case ADMIN -> {
                // No profile in this module for admins.
            }
        }
    }

    /**
     * Updates the display-only `verified` cache exposed on
     * CustomerProfileSummary/DriverProfileSummary. This is what satisfies
     * "subscribe to the event, don't poll" - but it is NOT what
     * DriverProfileService.setOnlineStatus checks before allowing ONLINE;
     * that does a live call instead. Reconciling those two requirements
     * this way is a judgment call - see the module README note.
     */
    @EventListener
    @Transactional
    public void onAccountVerified(AccountVerified event) {
        switch (event.role()) {
            case CUSTOMER -> customerProfileRepository.findByAccountId(event.accountId())
                    .ifPresent(profile -> {
                        profile.setVerified(true);
                        customerProfileRepository.save(profile);
                    });
            case DRIVER -> driverProfileRepository.findByAccountId(event.accountId())
                    .ifPresent(profile -> {
                        profile.setVerified(true);
                        driverProfileRepository.save(profile);
                    });
            case ADMIN -> {
                // AccountVerified is never published for ADMIN accounts.
            }
        }
    }

    // -----------------------------------------------------------------------
    // Cancellation accountability.
    //
    // Three listeners maintain two counters per profile. The denominators are
    // deliberately different on the two sides, because "a booking you were
    // party to" does not mean the same thing to a rider and to a partner:
    //
    //   - a rider's denominator is every booking she requested, counted the
    //     moment she requests it;
    //   - a partner's is every booking she was assigned, counted at MATCHED.
    //
    // MATCHED rather than ACCEPTED is the partner's denominator for two
    // reasons. It is only ever reached because she took an offer - a declined
    // offer never produces it - so declining cannot quietly dilute anyone's
    // rate. And a partner can cancel a MATCHED booking she never went on to
    // accept, so counting from ACCEPTED would let a cancellation land with no
    // booking underneath it and produce a rate above 100%.
    // -----------------------------------------------------------------------

    /** A rider's denominator: every booking she asked for. */
    @EventListener
    @Transactional
    public void onBookingRequested(BookingRequested event) {
        customerProfileRepository.findByAccountId(event.customerId())
                .ifPresent(profile -> {
                    profile.recordBooking();
                    customerProfileRepository.save(profile);
                });
    }

    /** A partner's denominator: every booking she was assigned. */
    @EventListener
    @Transactional
    public void onBookingMatched(BookingMatched event) {
        driverProfileRepository.findByAccountId(event.driverId())
                .ifPresent(profile -> {
                    profile.recordBooking();
                    driverProfileRepository.save(profile);
                });
    }

    /**
     * Counts a cancellation against whoever actually made it.
     * <p>
     * Attributed by cancelledBy, and never to both sides. A booking has
     * exactly one party who cancelled it; the other is the party it was done
     * to, and charging her for it would be the opposite of accountability.
     * This is why BookingCancelled had to start carrying cancelledBy - a
     * booking id alone cannot say whose cancellation it was.
     */
    @EventListener
    @Transactional
    public void onBookingCancelled(BookingCancelled event) {
        UUID cancelledBy = event.cancelledBy();
        if (cancelledBy == null) {
            // A system-initiated cancellation, which nothing does today. It
            // is nobody's fault, so it is nobody's number.
            return;
        }

        if (cancelledBy.equals(event.customerId())) {
            customerProfileRepository.findByAccountId(cancelledBy).ifPresent(profile -> {
                profile.recordCancellation();
                reviewIfNeeded(profile.getAccountId(), "customer",
                        profile.getTotalBookings(), profile.getTotalCancellations(), profile::flagForReview);
                customerProfileRepository.save(profile);
            });
        } else if (cancelledBy.equals(event.driverId())) {
            driverProfileRepository.findByAccountId(cancelledBy).ifPresent(profile -> {
                profile.recordCancellation();
                reviewIfNeeded(profile.getAccountId(), "driver",
                        profile.getTotalBookings(), profile.getTotalCancellations(), profile::flagForReview);
                driverProfileRepository.save(profile);
            });
        } else {
            // Somebody other than the two participants cancelled. Not counted
            // against either of them, because neither of them did it.
            log.debug("Cancellation of booking {} was not by a participant - not counted", event.bookingId());
        }
    }

    /**
     * Raises the review flag if this account has crossed the line - and only
     * raises a flag.
     * <p>
     * No automatic block, deliberately, and consistent with how driver
     * verification already works here: a person decides. It matters more for
     * cancellations than for documents, because a high rate has two opposite
     * explanations - somebody dodging fares, or somebody repeatedly abandoned
     * by partners who never turned up - and the number on its own cannot tell
     * them apart. An operator reading the reasons can.
     */
    private void reviewIfNeeded(UUID accountId, String role, int totalBookings, int totalCancellations,
                                Consumer<String> flag) {
        if (!cancellationPolicy.shouldFlag(totalBookings, totalCancellations)) {
            return;
        }
        String reason = cancellationPolicy.describe(totalBookings, totalCancellations);
        flag.accept(reason);
        log.info("Flagged {} account {} for review: {}", role, accountId, reason);
    }
}
