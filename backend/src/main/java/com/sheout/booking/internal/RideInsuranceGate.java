package com.sheout.booking.internal;

import com.sheout.booking.BookingType;
import com.sheout.insurance.InsuranceApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * "No ride without passenger cover", when that is the rule.
 * <p>
 * The 2025 aggregator guidelines expect every passenger trip to carry at
 * least ₹5 lakh of cover. With INSURANCE_REQUIRED_FOR_RIDES on (production)
 * and no passenger policy in force, a ride request is refused with a reason,
 * rather than taken and driven uninsured. Off by default locally, where
 * nobody has a policy. Deliveries are not refused: goods cover is not what
 * the guidelines ask of every trip.
 */
@Component
class RideInsuranceGate {

    private final InsuranceApi insurance;
    private final boolean requiredForRides;

    RideInsuranceGate(InsuranceApi insurance,
                      @Value("${sheout.insurance.required-for-rides:false}") boolean requiredForRides) {
        this.insurance = insurance;
        this.requiredForRides = requiredForRides;
    }

    boolean refuses(BookingType type) {
        return requiredForRides && type == BookingType.RIDE && !insurance.passengerCoverActive();
    }
}
