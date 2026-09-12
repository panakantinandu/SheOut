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

    /**
     * The ids of every booking this customer has made.
     *
     * Added for one caller: payments needs to list a customer's payment
     * history, and a payment row knows only its bookingId - there is no
     * customerId on it. The alternatives were worse. Denormalising a
     * customerId into payments would put a users concept inside a module
     * that has no business knowing about one, and would need backfilling.
     * Returning full summaries here would duplicate BookingService's own
     * list method into the public interface for no gain.
     *
     * FLAGGED: this becomes an IN clause the size of the customer's whole
     * history. Fine at the scale of one person's trips; if a single account
     * ever accumulates thousands, payments needs its own scoping column
     * rather than a bigger list.
     */
    java.util.Set<java.util.UUID> bookingIdsForCustomer(java.util.UUID customerId);

    /**
     * A page of every booking, narrowed by {@link BookingQuery}. No owner
     * scope - this is the ops console's view.
     * <p>
     * findRecent above is what the console used to call, and it is kept
     * because nothing has stopped calling it yet. It cannot do this job: a
     * hard cap with no offset means the rows past the cap are unreachable,
     * and no filters means an operator looking for one cancelled trip last
     * Tuesday has to read the whole page themselves.
     */
    org.springframework.data.domain.Page<BookingSummary> pageBookings(
            BookingQuery query, org.springframework.data.domain.Pageable pageable);
}
