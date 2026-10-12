package com.sheout.booking;

/**
 * Public (unlike most other modules' *Error enums, which stay internal)
 * because {@link BookingApi} is a real cross-module interface - dispatch
 * will call {@code assignDriver} and needs to be able to interpret what
 * comes back, the same way a caller of any public method needs its
 * checked/declared failure type.
 */
public enum BookingError {
    CUSTOMER_NOT_VERIFIED,
    /**
     * A parcel note with a phone number in it. Riders' and partners' numbers
     * are never passed between them - calls go through SheOut - so a note
     * cannot be the way round that.
     */
    PARCEL_NOTE_HAS_PHONE,
    /**
     * A rider account with no verified phone number - one created through
     * Google that has not added one yet. Dispatch, SOS and support all reach
     * a rider on her number, so a trip is never booked without one.
     */
    CUSTOMER_PHONE_REQUIRED,
    /** Pickup or drop is outside the radius SheOut operates in - see ServiceArea. */
    OUTSIDE_SERVICE_AREA,
    /** Outside the operating hours, or paused by an operator - see ServiceHoursApi. */
    SERVICE_CLOSED,
    /**
     * Rides need passenger insurance in force (INSURANCE_REQUIRED_FOR_RIDES)
     * and no passenger policy is active - see booking's RideInsuranceGate.
     */
    RIDE_INSURANCE_NOT_ACTIVE,
    /** Booked near closing time, the trip would end too long after it - see ServiceHoursApi.latestTripFinish. */
    TRIP_ENDS_AFTER_HOURS,
    CATEGORY_TYPE_MISMATCH,
    BOOKING_NOT_FOUND,

    /** Cancelling without saying why. See CancellationReason. */
    CANCELLATION_REASON_REQUIRED,

    /** Reason OTHER with no note - an answer that answers nothing. */
    CANCELLATION_NOTE_REQUIRED,

    /** A reason that is not hers to give (a rider blaming herself, a partner reporting her own vehicle), or not yet possible. */
    CANCELLATION_REASON_NOT_ALLOWED,
    INVALID_STATE_TRANSITION,

    /**
     * The partner typed a code, and it was not this booking's code. Said
     * plainly to her, because the alternative - a trip that silently does
     * not start - leaves her standing at a kerb with no idea why.
     */
    INVALID_PICKUP_CODE,

    /**
     * No code submitted at all. Distinct from a wrong one so an out-of-date
     * client gets told what it is missing rather than being accused of
     * getting it wrong.
     */
    PICKUP_CODE_REQUIRED,

    /** Too many wrong guesses on this booking. See PickupCode.MAX_ATTEMPTS. */
    PICKUP_VERIFICATION_LOCKED,

    /** The driver has not reached the authoritative drop-off location yet. */
    DRIVER_NOT_AT_DROP_OFF,

    /** Ending away from the drop (or with no trustworthy position) needs a DropOffDeviationReason. */
    DROP_OFF_REASON_REQUIRED,

    /** DropOffDeviationReason.OTHER needs a few words. */
    DROP_OFF_NOTE_REQUIRED,

    /** The driver has not reached the authoritative pickup location yet. */
    DRIVER_NOT_AT_PICKUP,

    /** The driver's last location is missing, invalid, or too old to trust. */
    DRIVER_LOCATION_UNAVAILABLE,

    /**
     * The rider has a trip that ended and is still unpaid, so she cannot
     * book another. Paying it - from the wallet or online - clears this.
     */
    UNPAID_TRIP,

    /**
     * She already has a live trip of this type - searching, matched, on the
     * way or under way. One at a time: every booking goes out to real
     * partners, and six at once from one account was six partners each
     * driving to a pickup for a trip that could only ever be one.
     */
    ACTIVE_BOOKING_EXISTS,

    /** She has already had her change of destination answered on this trip. See DestinationChangeService. */
    DESTINATION_CHANGE_LIMIT_REACHED,

    /** A change is already waiting for her partner's answer. One question at a time. */
    DESTINATION_CHANGE_PENDING,

    /** The new drop is where the trip is already going. */
    DESTINATION_UNCHANGED,

    /** The fare she was shown is not what the trip now prices at; she is shown the new one before anything is sent. */
    DESTINATION_FARE_CHANGED,

    /** Answering a change that is no longer waiting - it ran out of time, or the trip has ended. */
    DESTINATION_CHANGE_NOT_PENDING,

    // ---- parcels: proof of delivery (see ParcelDeliveryService) ----

    /** A delivery booked without who it is for, or without agreeing to the item and size rules. */
    PARCEL_DETAILS_REQUIRED,
    /** The receiver's number is not a 10-digit Indian mobile number. */
    PARCEL_RECEIVER_PHONE_INVALID,
    /** She agreed to an older prohibited-items list than the one in force; she is shown the new one. */
    PARCEL_RULES_CHANGED,
    PARCEL_DECLARED_VALUE_TOO_HIGH,
    /** The parcel is photographed as it is handed over, before the trip starts. */
    PARCEL_PICKUP_PHOTO_REQUIRED,
    /** The handover is photographed before the parcel is marked delivered. */
    PARCEL_DELIVERY_PHOTO_REQUIRED,
    /** A delivery ends on the receiver's code (or the sender's "leave it with" choice). */
    PARCEL_DELIVERY_CODE_REQUIRED,
    INVALID_PARCEL_DELIVERY_CODE,
    PARCEL_DELIVERY_CODE_LOCKED,
    /** "Receiver not there" needs a photo of the door or the place first. */
    PARCEL_EXCEPTION_PHOTO_REQUIRED,
    PARCEL_EXCEPTION_NOTE_REQUIRED,
    /** The sender answers only while the partner is waiting for her answer. */
    PARCEL_NO_EXCEPTION_PENDING,
    PARCEL_LEAVE_WITH_NAME_REQUIRED,
    /** Not a photo the server can take: not an image, empty, or too big. */
    PARCEL_PHOTO_INVALID,
    /** The sender is not at the drop, so she cannot end a delivery "here". */
    PARCEL_END_HERE_NOT_ALLOWED,
    /** The return fare is already part of a charge; refund it instead. */
    PARCEL_RETURN_FARE_ALREADY_CHARGED,
    PARCEL_DISPUTE_ALREADY_OPEN,
    PARCEL_NO_DISPUTE_OPEN,
    /** A ride, or a delivery booked before proof of delivery. */
    NOT_A_PARCEL
}
