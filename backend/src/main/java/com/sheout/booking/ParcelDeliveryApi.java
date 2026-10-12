package com.sheout.booking;

import com.sheout.sharedkernel.Result;

import java.util.Optional;
import java.util.UUID;

/**
 * Parcel proof of delivery, for other modules - the admin console's evidence
 * screen and its decisions. Every decision needs a note: it is the record of
 * what was decided, and why.
 */
public interface ParcelDeliveryApi {

    /** Empty for a ride, and for a delivery booked before proof of delivery existed. */
    Optional<ParcelEvidence> evidence(UUID bookingId);

    /** Keeps the photos past retention and the partner's earnings held (ParcelDisputeOpened). */
    Result<ParcelEvidence, BookingError> openDispute(UUID bookingId, UUID adminAccountId, String note);

    /** Releases or withholds the partner's held earnings (ParcelDisputeClosed). */
    Result<ParcelEvidence, BookingError> closeDispute(UUID bookingId, UUID adminAccountId, ParcelDisputeOutcome outcome, String note);

    /** An operator has looked at a delivery flagged by the drop check. Nothing else changes. */
    Result<ParcelEvidence, BookingError> markReviewed(UUID bookingId, UUID adminAccountId, String note);

    /** Takes the return fare off a trip still under way, when the partner was at fault. */
    Result<ParcelEvidence, BookingError> waiveReturnFare(UUID bookingId, UUID adminAccountId, String note);
}
