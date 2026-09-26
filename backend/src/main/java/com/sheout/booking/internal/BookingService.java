package com.sheout.booking.internal;

import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingAccepted;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingMatched;
import com.sheout.booking.BookingParticipants;
import com.sheout.booking.BookingRequested;
import com.sheout.booking.BookingStarted;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.CancellationReason;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.PaymentHold;
import com.sheout.booking.RequestBookingCommand;
import com.sheout.booking.internal.fare.FareCalculator;
import com.sheout.booking.internal.fare.FareQuote;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.geo.ServiceArea;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import com.sheout.booking.BookingQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService implements BookingApi {

    /** A partner on any of these is busy - see hasActiveTripAsDriver. */
    private static final java.util.Set<BookingStatus> ACTIVE_TRIP_STATUSES = java.util.EnumSet.of(
            BookingStatus.MATCHED, BookingStatus.ACCEPTED, BookingStatus.IN_PROGRESS);

    /** A rider's trip is live from the moment it starts searching. */
    private static final java.util.Set<BookingStatus> LIVE_STATUSES = java.util.EnumSet.of(
            BookingStatus.REQUESTED, BookingStatus.MATCHED, BookingStatus.ACCEPTED, BookingStatus.IN_PROGRESS);

    private final BookingRepository bookingRepository;
    private final VerificationApi verificationApi;
    private final FareCalculator fareCalculator;
    private final DomainEventPublisher eventPublisher;
    private final AuthApi authApi;
    private final ServiceArea serviceArea;
    private final String verifiedBypassPhone;
    private final Duration partnerPaymentHold;
    private final DriverLocationApi locationStore;
    private final DropoffGeofence dropoffGeofence;
    private final DropoffGeofence pickupGeofence;

    @Autowired
    public BookingService(BookingRepository bookingRepository,
                           VerificationApi verificationApi,
                           FareCalculator fareCalculator,
                           DomainEventPublisher eventPublisher,
                           AuthApi authApi,
                           ServiceArea serviceArea,
                           @Value("${sheout.testing.verified-bypass-phone:}") String verifiedBypassPhone,
                           @Value("${sheout.booking.partner-payment-hold-minutes:10}") long partnerPaymentHoldMinutes,
                           DriverLocationApi locationStore,
                           @Value("${sheout.booking.completion.drop-off-radius-metres:100}") double dropoffRadiusMetres,
                           @Value("${sheout.booking.completion.driver-location-max-age-seconds:30}") long driverLocationMaxAgeSeconds,
                           @Value("${sheout.dispatch.arriving-radius-metres:300}") double pickupRadiusMetres) {
        this.partnerPaymentHold = Duration.ofMinutes(partnerPaymentHoldMinutes);
        this.bookingRepository = bookingRepository;
        this.verificationApi = verificationApi;
        this.fareCalculator = fareCalculator;
        this.eventPublisher = eventPublisher;
        this.authApi = authApi;
        this.serviceArea = serviceArea;
        this.verifiedBypassPhone = verifiedBypassPhone;
        this.locationStore = locationStore;
        this.dropoffGeofence = new DropoffGeofence(
                dropoffRadiusMetres, Duration.ofSeconds(driverLocationMaxAgeSeconds));
        this.pickupGeofence = new DropoffGeofence(
                pickupRadiusMetres, Duration.ofSeconds(driverLocationMaxAgeSeconds));
    }

    /**
     * Keeps direct unit-test construction source/binary compatible while the
     * Spring constructor also receives the pickup radius configuration.
     */
    public BookingService(BookingRepository bookingRepository,
                           VerificationApi verificationApi,
                           FareCalculator fareCalculator,
                           DomainEventPublisher eventPublisher,
                           AuthApi authApi,
                           ServiceArea serviceArea,
                           String verifiedBypassPhone,
                           long partnerPaymentHoldMinutes,
                           DriverLocationApi locationStore,
                           double dropoffRadiusMetres,
                           long driverLocationMaxAgeSeconds) {
        this(bookingRepository, verificationApi, fareCalculator, eventPublisher, authApi, serviceArea,
                verifiedBypassPhone, partnerPaymentHoldMinutes, locationStore, dropoffRadiusMetres,
                driverLocationMaxAgeSeconds, 300);
    }

    /**
     * A fare quote and nothing else: no entity, no event, no side effect of
     * any kind. It runs the same FareCalculator requestBooking runs, with
     * the same inputs, so the number a customer is shown before booking is
     * the number the booking is then created with.
     * <p>
     * Deliberately not transactional and deliberately not persisted - it
     * reads nothing and writes nothing. It also does not check customer
     * verification: pricing is public information, and refusing to price a
     * trip would leak whether an account is verified to anyone who asks.
     * The verification gate stays where it belongs, on actually creating
     * the booking.
     */
    public Result<FareQuote, BookingError> quoteFare(BookingCategory category, GeoAddress pickup, GeoAddress drop) {
        if (!withinServiceArea(pickup, drop)) {
            return Result.failure(BookingError.OUTSIDE_SERVICE_AREA);
        }
        return Result.success(fareCalculator.quote(category, pickup, drop));
    }

    /**
     * Both ends must be somewhere we operate. Checked here rather than in
     * the controller so the booking path and the quote path cannot drift
     * apart, and so no caller can reach a booking without passing it.
     */
    private boolean withinServiceArea(GeoAddress pickup, GeoAddress drop) {
        return serviceArea.covers(pickup.lat(), pickup.lng()) && serviceArea.covers(drop.lat(), drop.lng());
    }

    @Override
    @Transactional
    public Result<BookingSummary, BookingError> requestBooking(RequestBookingCommand command) {
        if (command.category().expectedType() != command.type()) {
            return Result.failure(BookingError.CATEGORY_TYPE_MISMATCH);
        }
        // Before the verification gate on purpose: whether we serve an area
        // is public information, so answering it first tells an unverified
        // customer the useful thing rather than the gate they would have
        // hit anyway.
        if (!withinServiceArea(command.pickup(), command.drop())) {
            return Result.failure(BookingError.OUTSIDE_SERVICE_AREA);
        }
        // Before the ID check: the app asks for the number first, so this is
        // the step she is actually on. The app enforces it too, but only the
        // server can promise a phone-less account never books.
        if (!hasPhoneNumber(command.customerId())) {
            return Result.failure(BookingError.CUSTOMER_PHONE_REQUIRED);
        }
        if (!isCustomerVerified(command.customerId())) {
            return Result.failure(BookingError.CUSTOMER_NOT_VERIFIED);
        }
        // A trip that ended unpaid has to be paid before the next one. This
        // is what makes walking away from a fare pointless: the account that
        // owes it cannot ride again until it is settled.
        if (findPaymentHoldForCustomer(command.customerId()).isPresent()) {
            return Result.failure(BookingError.UNPAID_TRIP);
        }
        // One live trip of each kind: a ride and a parcel together is
        // ordinary, two rides at once is not a thing a person does.
        if (bookingRepository.existsByCustomerIdAndTypeAndStatusIn(command.customerId(), command.type(), LIVE_STATUSES)) {
            return Result.failure(BookingError.ACTIVE_BOOKING_EXISTS);
        }

        BigDecimal fareEstimate = fareCalculator.estimate(command.category(), command.pickup(), command.drop());
        BookingEntity booking = new BookingEntity(
                command.type(),
                command.category(),
                command.customerId(),
                GeoAddressEmbeddable.from(command.pickup()),
                GeoAddressEmbeddable.from(command.drop()),
                fareEstimate
        );
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingRequested(
                booking.getId(), booking.getCustomerId(), booking.getType(), booking.getCategory(),
                command.pickup(), command.drop()));

        return Result.success(toSummary(booking));
    }

    @Override
    @Transactional
    public Result<BookingSummary, BookingError> assignDriver(UUID bookingId, UUID driverId) {
        Optional<BookingEntity> found = bookingRepository.findLockedById(bookingId);
        if (found.isEmpty()) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();

        Result<BookingStatus, BookingError> transition =
                BookingStateMachine.transition(booking.getStatus(), BookingStatus.MATCHED);
        if (transition.isFailure()) {
            return Result.failure(transition.error());
        }

        booking.setDriverId(driverId);
        booking.setStatus(BookingStatus.MATCHED);
        booking.setMatchedAt(Instant.now());
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingMatched(booking.getId(), booking.getCustomerId(), driverId));
        return Result.success(toSummary(booking));
    }

    /** Self-service - the driver assigned to a MATCHED booking confirms it. Caller-vs-driverId match is checked by the controller. */
    @Transactional
    public Result<BookingSummary, BookingError> acceptBooking(UUID bookingId) {
        Optional<BookingEntity> found = bookingRepository.findLockedById(bookingId);
        if (found.isEmpty()) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();

        Result<BookingStatus, BookingError> transition =
                BookingStateMachine.transition(booking.getStatus(), BookingStatus.ACCEPTED);
        if (transition.isFailure()) {
            return Result.failure(transition.error());
        }

        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setAcceptedAt(Instant.now());
        // Generated here, at the one moment a partner becomes committed to
        // this trip, so the rider's screen has a code to show her from the
        // instant she is told somebody is coming. Generating it later - at
        // arrival, say - would need an "arrived" state the booking module
        // does not have, and would leave a window where she is watching a
        // partner approach with nothing to read out.
        booking.setPickupOtp(PickupCode.generate());
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingAccepted(booking.getId(), booking.getCustomerId(), booking.getDriverId()));
        return Result.success(toSummary(booking));
    }

    /**
     * The partner proves she is at the pickup, and the trip begins.
     * <p>
     * This replaces an unverified "Start Trip" tap. That tap let a partner
     * move a booking to IN_PROGRESS and then COMPLETED with nobody in the
     * vehicle, and the rider was charged the fare for it. The code is the
     * only thing standing between the two, so it is checked here - in the
     * same transaction that moves the status - rather than in the
     * controller, where a second caller could one day skip it.
     * <p>
     * Order matters, and it is deliberate:
     * <ol>
     *   <li>the state machine first, so a booking that cannot start is
     *       refused before a guess is ever counted against it - otherwise
     *       replaying a stale request would burn a legitimate partner's
     *       attempts;</li>
     *   <li>the attempt limit next, so an exhausted booking stops accepting
     *       guesses rather than merely recording them;</li>
     *   <li>the code last.</li>
     * </ol>
     * <p>
     * The authenticated driver and a recent pickup-geofence location are
     * checked inside this locked transaction, before the OTP is consumed or
     * the status is changed.
     */
    @Transactional
    public Result<BookingSummary, BookingError> startTrip(UUID bookingId, UUID driverId, String submittedCode) {
        // Locked: the attempt count below is read, compared and written back,
        // and concurrent guesses must see each other's count. See
        // BookingRepository.findLockedById.
        Optional<BookingEntity> found = bookingRepository.findLockedById(bookingId);
        if (found.isEmpty()) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();

        if (!driverId.equals(booking.getDriverId())) {
            return Result.failure(BookingError.INVALID_STATE_TRANSITION);
        }

        Result<BookingStatus, BookingError> transition =
                BookingStateMachine.transition(booking.getStatus(), BookingStatus.IN_PROGRESS);
        if (transition.isFailure()) {
            return Result.failure(transition.error());
        }

        DropoffGeofence.Decision pickupDecision = pickupGeofence.check(
                locationStore.findLocation(driverId).orElse(null),
                booking.getPickup().getLat(), booking.getPickup().getLng(), Instant.now());
        if (pickupDecision == DropoffGeofence.Decision.LOCATION_UNAVAILABLE) {
            return Result.failure(BookingError.DRIVER_LOCATION_UNAVAILABLE);
        }
        if (pickupDecision != DropoffGeofence.Decision.AT_DROP_OFF) {
            return Result.failure(BookingError.DRIVER_NOT_AT_PICKUP);
        }

        if (booking.getPickupOtp() == null) {
            return Result.failure(BookingError.PICKUP_CODE_REQUIRED);
        }
        if (booking.pickupAttemptsExhausted()) {
            return Result.failure(BookingError.PICKUP_VERIFICATION_LOCKED);
        }
        if (!PickupCode.isWellFormed(submittedCode)) {
            return Result.failure(BookingError.PICKUP_CODE_REQUIRED);
        }
        if (!PickupCode.matches(booking.getPickupOtp(), submittedCode)) {
            // Saved on its own, because the surrounding transaction is
            // about to return a failure Result - which does not roll
            // back, but leaving the count to an implicit flush would
            // make the count difficult to reason about.
            booking.recordFailedPickupAttempt();
            bookingRepository.save(booking);
            return Result.failure(booking.pickupAttemptsExhausted()
                    ? BookingError.PICKUP_VERIFICATION_LOCKED
                    : BookingError.INVALID_PICKUP_CODE);
        }

        Instant now = Instant.now();
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setStartedAt(now);
        booking.setPickupVerifiedAt(now);
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingStarted(
                booking.getId(), booking.getCustomerId(), booking.getDriverId(), true));
        return Result.success(toSummary(booking));
    }

    /**
     * The pickup code, for the rider who booked and nobody else.
     * <p>
     * Returned as an Optional rather than put on {@link BookingSummary},
     * and that is the whole design. {@code GET /bookings/{id}} is served to
     * both participants; a field on the summary would hand the partner the
     * code she is supposed to be proving she was told, which would make the
     * check worthless. So the code has its own read path, and the only
     * caller is an endpoint that has already established the caller is the
     * customer.
     */
    public Optional<String> findPickupCodeForCustomer(UUID bookingId, UUID customerId) {
        return bookingRepository.findById(bookingId)
                .filter(booking -> booking.getCustomerId().equals(customerId))
                .filter(booking -> booking.getStatus() == BookingStatus.ACCEPTED)
                .filter(booking -> booking.getDriverId() != null)
                .filter(booking -> pickupGeofence.check(
                        locationStore.findLocation(booking.getDriverId()).orElse(null),
                        booking.getPickup().getLat(), booking.getPickup().getLng(), Instant.now())
                        == DropoffGeofence.Decision.AT_DROP_OFF)
                .map(BookingEntity::getPickupOtp);
    }

    public boolean isDriverAtPickup(UUID bookingId, UUID driverId) {
        return bookingRepository.findById(bookingId)
                .filter(booking -> booking.getDriverId() != null && booking.getDriverId().equals(driverId))
                .filter(booking -> booking.getStatus() == BookingStatus.ACCEPTED)
                .map(booking -> pickupGeofence.check(
                        locationStore.findLocation(driverId).orElse(null),
                        booking.getPickup().getLat(), booking.getPickup().getLng(), Instant.now())
                        == DropoffGeofence.Decision.AT_DROP_OFF)
                .orElse(false);
    }

    /**
     * Self-service - the assigned driver marks drop-off complete. The row
     * lock keeps duplicate requests serialized; the authoritative drop-off
     * and the latest trusted driver location are checked before any state or
     * payment-related event can be written.
     */
    @Transactional
    public Result<BookingSummary, BookingError> completeTrip(UUID bookingId, UUID driverId) {
        // Lock before every read involved in completion. This makes the state
        // check, location check, and final transition one atomic decision.
        Optional<BookingEntity> found = bookingRepository.findLockedById(bookingId);
        if (found.isEmpty()) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();

        Result<BookingStatus, BookingError> transition =
                BookingStateMachine.transition(booking.getStatus(), BookingStatus.COMPLETED);
        if (transition.isFailure()) {
            return Result.failure(transition.error());
        }
        if (booking.getDriverId() == null || !booking.getDriverId().equals(driverId)) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }

        Instant now = Instant.now();
        Optional<DriverLocation> location = locationStore.findLocation(driverId);
        if (location.isEmpty()) {
            return Result.failure(BookingError.DRIVER_LOCATION_UNAVAILABLE);
        }
        DropoffGeofence.Decision geofence = dropoffGeofence.check(
                location.get(), booking.getDrop().getLat(), booking.getDrop().getLng(), now);
        if (geofence == DropoffGeofence.Decision.LOCATION_UNAVAILABLE) {
            return Result.failure(BookingError.DRIVER_LOCATION_UNAVAILABLE);
        }
        if (geofence == DropoffGeofence.Decision.OUTSIDE_DROP_OFF) {
            return Result.failure(BookingError.DRIVER_NOT_AT_DROP_OFF);
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(now);
        booking.setFinalFare(booking.getFareEstimate());
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingCompleted(
                booking.getId(), booking.getCustomerId(), booking.getDriverId(), booking.getFinalFare()));
        return Result.success(toSummary(booking));
    }

    /**
     * The rider ends her own trip where she is, short of the drop pin.
     * <p>
     * The partner's End Trip is held to the drop-off geofence, which is right
     * for her and left no way out for the ordinary "drop me here, by the
     * gate": the partner could not end it, the rider had no button, and the
     * trip sat IN_PROGRESS until support stepped in. The rider is the one
     * person who can say she has arrived without GPS having to agree, so the
     * rider may end it. The fare is the one she was quoted - stopping early
     * is her choice and never costs her partner.
     */
    @Transactional
    public Result<BookingSummary, BookingError> endTripAtRidersRequest(UUID bookingId, UUID customerId) {
        Optional<BookingEntity> found = bookingRepository.findLockedById(bookingId);
        if (found.isEmpty() || !found.get().getCustomerId().equals(customerId)) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();
        Result<BookingStatus, BookingError> transition =
                BookingStateMachine.transition(booking.getStatus(), BookingStatus.COMPLETED);
        if (transition.isFailure()) {
            return Result.failure(transition.error());
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now());
        booking.setFinalFare(booking.getFareEstimate());
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingCompleted(
                booking.getId(), booking.getCustomerId(), booking.getDriverId(), booking.getFinalFare()));
        return Result.success(toSummary(booking));
    }


    /**
     * Self-service - either participant (customer or the assigned driver)
     * can cancel pre-IN_PROGRESS. Which of them it was is checked by the
     * controller and recorded here.
     * <p>
     * Cancels a booking, recording who did it and why.
     * <p>
     * The reason is required rather than optional, which BookingCancelled's
     * own Javadoc already flagged as the gap. Without it a cancellation is a
     * fact with no explanation: a rider who changed their mind and one whose
     * partner never arrived were counted identically, and any accountability
     * built on that count would have been unfair in both directions.
     * <p>
     * OTHER additionally requires a note. An "Other" with nothing after it
     * is the same as no reason, dressed up as an answer.
     */
    /**
     * Records that dispatch searched, found nobody, and has stopped.
     * <p>
     * Called only from DispatchExhaustedListener. It takes no reason and no
     * actor because there is neither: this is not somebody's decision, it is
     * the platform failing to find a driver, and the distinction is the
     * whole reason NO_DRIVERS_AVAILABLE is not CANCELLED. In particular it
     * publishes no BookingCancelled, so no cancellation is counted against
     * the rider's trust score for a search she had no part in.
     * <p>
     * Only valid from REQUESTED. A booking that has since been cancelled by
     * the rider, or matched by a driver who accepted in the gap, is left
     * exactly as it is - the state machine refuses the transition and this
     * returns that refusal rather than forcing it.
     */
    @Transactional
    public Result<BookingSummary, BookingError> markNoDriversAvailable(UUID bookingId) {
        Optional<BookingEntity> found = bookingRepository.findLockedById(bookingId);
        if (found.isEmpty()) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();

        Result<BookingStatus, BookingError> transition =
                BookingStateMachine.transition(booking.getStatus(), BookingStatus.NO_DRIVERS_AVAILABLE);
        if (transition.isFailure()) {
            return Result.failure(transition.error());
        }

        booking.setStatus(BookingStatus.NO_DRIVERS_AVAILABLE);
        bookingRepository.save(booking);
        return Result.success(toSummary(booking));
    }

    @Transactional
    public Result<BookingSummary, BookingError> cancelBooking(
            UUID bookingId, UUID cancelledBy, CancellationReason reason, String note) {
        if (reason == null) {
            return Result.failure(BookingError.CANCELLATION_REASON_REQUIRED);
        }
        String trimmedNote = note == null ? null : note.trim();
        if (reason.requiresNote() && (trimmedNote == null || trimmedNote.isBlank())) {
            return Result.failure(BookingError.CANCELLATION_NOTE_REQUIRED);
        }

        Optional<BookingEntity> found = bookingRepository.findLockedById(bookingId);
        if (found.isEmpty()) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();

        Result<BookingStatus, BookingError> transition =
                BookingStateMachine.transition(booking.getStatus(), BookingStatus.CANCELLED);
        if (transition.isFailure()) {
            return Result.failure(transition.error());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());
        booking.recordCancellation(cancelledBy, reason, trimmedNote);
        bookingRepository.save(booking);

        // cancelledBy and the reason travel on the event, because the module
        // that counts cancellations against an account cannot work out
        // whose fault it was from a booking id alone.
        eventPublisher.publish(new BookingCancelled(
                booking.getId(), booking.getCustomerId(), booking.getDriverId(), cancelledBy, reason));
        return Result.success(toSummary(booking));
    }

    @Override
    public Optional<BookingSummary> findById(UUID bookingId) {
        return bookingRepository.findById(bookingId).map(this::toSummary);
    }

    @Override
    public List<BookingSummary> findRecent(int limit) {
        return bookingRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public Result<BookingParticipants, BookingError> getParticipants(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .<Result<BookingParticipants, BookingError>>map(
                        b -> Result.success(new BookingParticipants(b.getCustomerId(), b.getDriverId(), b.getStatus())))
                .orElseGet(() -> Result.failure(BookingError.BOOKING_NOT_FOUND));
    }

    public List<BookingSummary> listForCustomer(UUID customerId) {
        return bookingRepository.findByCustomerId(customerId).stream().map(this::toSummary).toList();
    }

    @Override
    public java.util.Set<UUID> bookingIdsForCustomer(UUID customerId) {
        return bookingRepository.findByCustomerId(customerId).stream()
                .map(BookingEntity::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public List<BookingSummary> findAllForAccount(UUID accountId) {
        return java.util.stream.Stream.concat(
                        bookingRepository.findByCustomerId(accountId).stream(),
                        bookingRepository.findByDriverId(accountId).stream())
                .distinct()
                .sorted(java.util.Comparator.comparing(BookingEntity::getCreatedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    public List<BookingSummary> listForDriver(UUID driverId) {
        return bookingRepository.findByDriverId(driverId).stream().map(this::toSummary).toList();
    }

    /**
     * A page of one person's own trips, narrowed by whatever they asked for.
     * <p>
     * The unpaged listForCustomer/listForDriver above are kept because
     * callers that genuinely need the whole set still exist - the driver
     * dashboard totals today's earnings across every completed trip, and
     * paging that would mean summing pages client-side and getting a
     * different answer depending on how far someone scrolled. Deleting them
     * in favour of paging everything would trade one honest number for
     * several inconsistent ones.
     */
    public Page<BookingSummary> pageForCustomer(UUID customerId, BookingQuery query, Pageable pageable) {
        return search(customerId, null, query, pageable);
    }

    public Page<BookingSummary> pageForDriver(UUID driverId, BookingQuery query, Pageable pageable) {
        return search(null, driverId, query, pageable);
    }

    /** No owner scope: the ops console sees every booking. */
    @Override
    public Page<BookingSummary> pageBookings(BookingQuery query, Pageable pageable) {
        return search(null, null, query, pageable);
    }

    private Page<BookingSummary> search(UUID customerId, UUID driverId, BookingQuery query, Pageable pageable) {
        // Newest first, applied here rather than in the Specification: the
        // sort belongs to the request, and every caller wants page 0 to be
        // the most recent. It also keeps offset paging stable enough - new
        // rows land on page 0 instead of shifting everything below them.
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return bookingRepository
                .findAll(BookingSpecs.matching(customerId, driverId, query), sorted)
                .map(this::toSummary);
    }

    /**
     * TESTING AID ONLY, NOT A PRODUCT FEATURE: if sheout.testing.verified-
     * bypass-phone is set (blank/unset by default in every real
     * environment, same as sheout.auth.dev-otp-phone) and this customer's
     * account phone number matches it exactly, the real verification check
     * below is skipped for this one account so an automated QA pass can
     * exercise the full booking flow without a real admin-verified test
     * account. Every other account - including every other test number -
     * still goes through the unmodified VerificationApi check with no
     * change in behavior.
     */
    private boolean hasPhoneNumber(UUID customerId) {
        return authApi.findAccount(customerId)
                .map(account -> account.phoneNumber() != null && !account.phoneNumber().isBlank())
                .orElse(false);
    }

    private boolean isCustomerVerified(UUID customerId) {
        if (isVerifiedBypassAccount(customerId)) {
            return true;
        }
        Optional<VerificationSummary> verification = verificationApi.findByAccountId(customerId);
        return verification.map(v -> v.genderVerificationStatus() == VerificationStatus.VERIFIED).orElse(false);
    }

    private boolean isVerifiedBypassAccount(UUID customerId) {
        if (verifiedBypassPhone.isBlank()) {
            return false;
        }
        return authApi.findAccount(customerId)
                .map(account -> verifiedBypassPhone.equals(account.phoneNumber()))
                .orElse(false);
    }

    private BookingSummary toSummary(BookingEntity booking) {
        return new BookingSummary(
                booking.getId(),
                booking.getType(),
                booking.getCategory(),
                booking.getStatus(),
                booking.getCustomerId(),
                booking.getDriverId(),
                booking.getPickup().toGeoAddress(),
                booking.getDrop().toGeoAddress(),
                booking.getFareEstimate(),
                booking.getFinalFare(),
                booking.getCreatedAt(),
                booking.getMatchedAt(),
                booking.getAcceptedAt(),
                booking.getStartedAt(),
                booking.getCompletedAt(),
                booking.getCancelledAt(),
                booking.getPaymentSettledAt()
        );
    }

    @Override
    @Transactional
    public Result<BookingSummary, BookingError> markPaymentSettled(UUID bookingId) {
        Optional<BookingEntity> found = bookingRepository.findLockedById(bookingId);
        if (found.isEmpty()) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();
        // Only an ended trip is paid for. Anything else reaching here is a
        // bug upstream, and settling a trip still in progress would free the
        // rider to book a second one while riding the first.
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            return Result.failure(BookingError.INVALID_STATE_TRANSITION);
        }
        if (booking.getPaymentSettledAt() == null) {
            booking.setPaymentSettledAt(Instant.now());
            bookingRepository.save(booking);
        }
        return Result.success(toSummary(booking));
    }

    @Override
    public Optional<PaymentHold> findPaymentHoldForCustomer(UUID customerId) {
        return bookingRepository
                .findFirstByCustomerIdAndStatusAndPaymentSettledAtIsNullOrderByCompletedAtAsc(
                        customerId, BookingStatus.COMPLETED)
                .map(booking -> new PaymentHold(booking.getId(), booking.getFinalFare(), booking.getCompletedAt(), null));
    }

    @Override
    public boolean hasActiveTripAsDriver(UUID driverId) {
        return bookingRepository.existsByDriverIdAndStatusIn(driverId, ACTIVE_TRIP_STATUSES);
    }

    public Optional<PaymentHold> findPaymentHoldForDriver(UUID driverId) {
        Instant cutoff = Instant.now().minus(partnerPaymentHold);
        return bookingRepository
                .findFirstByDriverIdAndStatusAndPaymentSettledAtIsNullAndCompletedAtAfterOrderByCompletedAtDesc(
                        driverId, BookingStatus.COMPLETED, cutoff)
                .map(booking -> new PaymentHold(booking.getId(), booking.getFinalFare(), booking.getCompletedAt(),
                        booking.getCompletedAt().plus(partnerPaymentHold)));
    }
}
