package com.sheout.insurance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about insurance. Coverage itself is opened and
 * closed by listening to booking's events, so nothing here writes.
 */
public interface InsuranceApi {

    /**
     * Whether a passenger policy is in force right now - active, and today
     * between its effective dates. Booking refuses rides on it when
     * INSURANCE_REQUIRED_FOR_RIDES is on.
     */
    boolean passengerCoverActive();

    /** The passenger cover in force, for the Safety Center's explainer; empty when there is none. */
    Optional<CoverSummary> currentPassengerCover();

    /**
     * The cover this trip had, when it had one that can be stood behind:
     * a coverage row from a policy that was active when the trip started,
     * not failed in reporting (and, if INSURANCE_BADGE_REQUIRES_REPORTED,
     * already reported). Empty means the apps say nothing about insurance.
     */
    Optional<TripCover> coverForTrip(UUID bookingId);

    /** Her group covers that the insurer has confirmed (ENROLLED); empty for anybody else. */
    List<CoverSummary> enrolledCoversFor(UUID partnerAccountId);
}
