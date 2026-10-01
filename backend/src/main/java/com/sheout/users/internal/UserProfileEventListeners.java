package com.sheout.users.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingMatched;
import com.sheout.booking.BookingRequested;
import com.sheout.driververification.AccountVerified;
import com.sheout.ratings.RatingSubmitted;
import com.sheout.users.TrustStats;
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
    private final TrustPolicy trustPolicy;

    UserProfileEventListeners(CustomerProfileRepository customerProfileRepository,
                               DriverProfileRepository driverProfileRepository,
                               TrustPolicy trustPolicy) {
        this.customerProfileRepository = customerProfileRepository;
        this.driverProfileRepository = driverProfileRepository;
        this.trustPolicy = trustPolicy;
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
    // Trust signals: how often an account cancels, and how it is rated.
    //
    // Both land here, on the same row, because they feed one flag and one
    // queue. An account is either waiting for a person to look at it or it is
    // not; two parallel flags would let it be cleared of one and stay
    // silently flagged for the other, and nobody working the queue could tell
    // when they had finished.
    //
    // The cancellation denominators are deliberately different on the two
    // sides, because "a booking you were party to" does not mean the same
    // thing to a rider and to a partner:
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
        // The same booking searching again after a partner dropped it: not a second booking of hers.
        if (event.isRestart()) {
            return;
        }
        customerProfileRepository.findByAccountId(event.customerId())
                .ifPresent(profile -> {
                    profile.recordBooking();
                    customerProfileRepository.save(profile);
                });
    }

    /** A partner dropped a trip she had taken; it went back to searching. Counted against her, like a cancellation. */
    @EventListener
    @Transactional
    public void onPartnerLeftBooking(com.sheout.booking.PartnerLeftBooking event) {
        driverProfileRepository.findByAccountId(event.driverId()).ifPresent(profile -> {
            profile.recordCancellation();
            reviewIfNeeded(profile.getAccountId(), "driver", profile.getTrustStats(), profile::flagForReview);
            driverProfileRepository.save(profile);
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
     * Counts a cancellation against the account booking says it belongs to,
     * and puts a reported account in front of an operator.
     * <p>
     * Never against both sides, and no longer simply against whoever pressed
     * cancel: a rider who gives up on a partner who never came, or who
     * refuses the wrong person or vehicle, did the right thing. Booking holds
     * the evidence (where the partner was, when she accepted) and decides;
     * see BookingCancelled.countsAgainst.
     */
    @EventListener
    @Transactional
    public void onBookingCancelled(BookingCancelled event) {
        // Booking decided, from where the partner was and how long she had
        // been coming, whose cancellation this is and who - if anybody - was
        // reported as the wrong person or vehicle, or not safe. Nothing is
        // re-judged here; see BookingService.judgeCancellation.
        if (event.reported() != null) {
            String reason = switch (event.reason()) {
                case WRONG_VEHICLE -> "Reported at pickup: not the vehicle shown in the app (trip " + event.bookingId() + ")";
                case SAFETY_CONCERN -> "Reported as a safety concern at pickup (trip " + event.bookingId() + ")";
                default -> "Reported at pickup as not the person shown in the app (trip " + event.bookingId() + ")";
            };
            if (event.reported().equals(event.driverId())) {
                driverProfileRepository.findByAccountId(event.reported()).ifPresent(profile -> {
                    profile.flagForReview(reason);
                    driverProfileRepository.save(profile);
                });
            } else {
                customerProfileRepository.findByAccountId(event.reported()).ifPresent(profile -> {
                    profile.flagForReview(reason);
                    customerProfileRepository.save(profile);
                });
            }
            log.warn("{} on booking {} reported by {}", event.reason(), event.bookingId(), event.cancelledBy());
        }
        UUID counted = event.countsAgainst();
        if (counted == null) {
            return;
        }
        if (counted.equals(event.customerId())) {
            customerProfileRepository.findByAccountId(counted).ifPresent(profile -> {
                profile.recordCancellation();
                reviewIfNeeded(profile.getAccountId(), "customer", profile.getTrustStats(), profile::flagForReview);
                customerProfileRepository.save(profile);
            });
        } else if (counted.equals(event.driverId())) {
            driverProfileRepository.findByAccountId(counted).ifPresent(profile -> {
                profile.recordCancellation();
                reviewIfNeeded(profile.getAccountId(), "driver", profile.getTrustStats(), profile::flagForReview);
                driverProfileRepository.save(profile);
            });
        }
    }

    /** Her selfie kept not matching - in front of an operator, with the reason. */
    @EventListener
    @Transactional
    public void onShiftCheckNeedsReview(com.sheout.driververification.ShiftCheckNeedsReview event) {
        driverProfileRepository.findByAccountId(event.accountId()).ifPresent(profile -> {
            profile.flagForReview("Start-of-shift selfie did not match her verified selfie several times running");
            driverProfileRepository.save(profile);
        });
    }

    /**
     * Keeps this account's rating figures in step with what the ratings
     * module just worked out, and re-checks whether that changes anything.
     * <p>
     * The average travels on the event rather than being recomputed here,
     * so the number an operator reads is the number the ratings module
     * calculated. Two modules independently averaging the same rows is how
     * they end up disagreeing about somebody's score.
     */
    @EventListener
    @Transactional
    public void onRatingSubmitted(RatingSubmitted event) {
        switch (event.ratedRole()) {
            case CUSTOMER -> customerProfileRepository.findByAccountId(event.ratedAccountId()).ifPresent(profile -> {
                profile.recordRatingAggregate(event.averageStars(), event.totalRatings());
                reviewIfNeeded(profile.getAccountId(), "customer", profile.getTrustStats(), profile::flagForReview);
                customerProfileRepository.save(profile);
            });
            case DRIVER -> driverProfileRepository.findByAccountId(event.ratedAccountId()).ifPresent(profile -> {
                profile.recordRatingAggregate(event.averageStars(), event.totalRatings());
                reviewIfNeeded(profile.getAccountId(), "driver", profile.getTrustStats(), profile::flagForReview);
                driverProfileRepository.save(profile);
            });
            case ADMIN -> {
                // Operators are not on trips, so they are never rated.
            }
        }
    }

    /**
     * Raises the review flag if this account has crossed a line - and only
     * raises a flag.
     * <p>
     * One check covering every trust signal, called from wherever any of
     * them moves, so an account that has become reviewable for a reason
     * other than the one that just changed still gets picked up. Both
     * reasons end up in the same sentence when both apply.
     * <p>
     * No automatic block, deliberately, and consistent with how driver
     * verification already works here: a person decides. It matters more
     * here than for documents, because both signals have an innocent reading
     * the number cannot distinguish from the guilty one - somebody dodging
     * fares, or somebody repeatedly abandoned by partners who never turned
     * up; a careless partner, or one who had three bad nights. An operator
     * reading the reasons can tell. Arithmetic cannot.
     */
    private void reviewIfNeeded(UUID accountId, String role, TrustStats stats, Consumer<String> flag) {
        trustPolicy.reviewReason(stats).ifPresent(reason -> {
            flag.accept(reason);
            log.info("Flagged {} account {} for review: {}", role, accountId, reason);
        });
    }
}
