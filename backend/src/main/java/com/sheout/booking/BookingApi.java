package com.sheout.booking;

import com.sheout.sharedkernel.Result;

import java.util.UUID;

/**
 * Deliberately minimal, exactly the two operations another module needs
 * right now: creating a booking, and dispatch assigning a driver to one.
 * Everything else a booking's own participants need (accepting, starting,
 * completing, cancelling, listing "my bookings") is self-service over
 * HTTP, internal to this module - see BookingController. This module does
 * not contain matching logic; assignDriver trusts its caller (dispatch,
 * once built) to have already picked an eligible driver.
 */
public interface BookingApi {

    Result<BookingSummary, BookingError> requestBooking(RequestBookingCommand command);

    Result<BookingSummary, BookingError> assignDriver(UUID bookingId, UUID driverId);
}
