package com.sheout.booking;

import com.sheout.sharedkernel.Result;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Deliberately minimal: creating a booking, dispatch assigning a driver to
 * one, and a read-only participants lookup for other modules' own
 * authorization checks (see getParticipants). Everything else a booking's
 * own participants need (accepting, starting, completing, cancelling,
 * listing "my bookings") is self-service over HTTP, internal to this
 * module - see BookingController. This module does not contain matching
 * logic; assignDriver trusts its caller (dispatch) to have already picked
 * an eligible driver.
 */
public interface BookingApi {

    Result<BookingSummary, BookingError> requestBooking(RequestBookingCommand command);

    Result<BookingSummary, BookingError> assignDriver(UUID bookingId, UUID driverId);

    /**
     * Just the two account ids a caller needs to check "is this account a
     * participant on this booking" - not the full BookingSummary, which
     * exposes far more than an authorization check needs (see
     * BookingParticipants). Added for payments' PaymentController, which
     * had no way to verify a caller before this (that gap was flagged in
     * this method's absence - see the old Javadoc history on this
     * interface and on BookingRequested).
     */
    Result<BookingParticipants, BookingError> getParticipants(UUID bookingId);

    /**
     * Same read-only reasoning as getParticipants, different shape: this
     * exposes the full BookingSummary (already public - see assignDriver's
     * return type) for callers that need pickup/drop/fare, not just
     * participant ids. Added for dispatch's own offer response - a driver
     * who's only been OFFERED a booking (not yet accepted) is correctly
     * NOT a participant per BookingController.requireParticipant, so
     * GET /api/v1/bookings/{id} 404s for them; dispatch needs to show
     * pickup/drop/fare on the offer itself before that acceptance happens.
     */
    Optional<BookingSummary> findById(UUID bookingId);

    /**
     * Most recently requested bookings across all customers, newest first,
     * capped at {@code limit}. Added for admin's operational list view -
     * before this, the only reads here were by-id or by-participant, so
     * "what is happening right now" could not be answered without querying
     * this module's tables directly.
     * <p>
     * Deliberately a simple capped list rather than a Pageable/filtered
     * query: the caller is an ops screen answering "what's happening now",
     * not a reporting surface, and a real pagination contract is worth
     * adding only once something actually needs to page.
     */
    List<BookingSummary> findRecent(int limit);
}
