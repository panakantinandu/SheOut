package com.sheout.booking;

/**
 * ASSUMPTION FLAGGED: not in the literal "a booking has" field list (which
 * only named {@code type}) - added because a booking with only
 * RIDE/DELIVERY and no vehicle/delivery sub-type isn't enough for dispatch
 * to match the right kind of driver, or for {@code FareCalculator} to
 * price it. {@link #expectedType()} is validated against the requested
 * {@link BookingType} when a booking is created (e.g. PARCEL can't be
 * requested as type RIDE) - see BookingService.
 */
public enum BookingCategory {
    BIKE(BookingType.RIDE),
    AUTO(BookingType.RIDE),
    CAB(BookingType.RIDE),
    PARCEL(BookingType.DELIVERY),
    LUNCHBOX(BookingType.DELIVERY);

    private final BookingType expectedType;

    BookingCategory(BookingType expectedType) {
        this.expectedType = expectedType;
    }

    public BookingType expectedType() {
        return expectedType;
    }
}
