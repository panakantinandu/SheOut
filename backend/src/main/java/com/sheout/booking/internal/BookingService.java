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
import com.sheout.booking.BookingSummary;
import com.sheout.booking.RequestBookingCommand;
import com.sheout.booking.internal.fare.FareCalculator;
import com.sheout.booking.internal.fare.FareQuote;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingService implements BookingApi {

    private final BookingRepository bookingRepository;
    private final VerificationApi verificationApi;
    private final FareCalculator fareCalculator;
    private final DomainEventPublisher eventPublisher;
    private final AuthApi authApi;
    private final String verifiedBypassPhone;

    public BookingService(BookingRepository bookingRepository,
                           VerificationApi verificationApi,
                           FareCalculator fareCalculator,
                           DomainEventPublisher eventPublisher,
                           AuthApi authApi,
                           @Value("${sheout.testing.verified-bypass-phone:}") String verifiedBypassPhone) {
        this.bookingRepository = bookingRepository;
        this.verificationApi = verificationApi;
        this.fareCalculator = fareCalculator;
        this.eventPublisher = eventPublisher;
        this.authApi = authApi;
        this.verifiedBypassPhone = verifiedBypassPhone;
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
    public FareQuote quoteFare(BookingCategory category, GeoAddress pickup, GeoAddress drop) {
        return fareCalculator.quote(category, pickup, drop);
    }

    @Override
    @Transactional
    public Result<BookingSummary, BookingError> requestBooking(RequestBookingCommand command) {
        if (command.category().expectedType() != command.type()) {
            return Result.failure(BookingError.CATEGORY_TYPE_MISMATCH);
        }
        if (!isCustomerVerified(command.customerId())) {
            return Result.failure(BookingError.CUSTOMER_NOT_VERIFIED);
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
        Optional<BookingEntity> found = bookingRepository.findById(bookingId);
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
        Optional<BookingEntity> found = bookingRepository.findById(bookingId);
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
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingAccepted(booking.getId(), booking.getCustomerId(), booking.getDriverId()));
        return Result.success(toSummary(booking));
    }

    /** Self-service - driver marks pickup complete / trip underway. */
    @Transactional
    public Result<BookingSummary, BookingError> startTrip(UUID bookingId) {
        Optional<BookingEntity> found = bookingRepository.findById(bookingId);
        if (found.isEmpty()) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();

        Result<BookingStatus, BookingError> transition =
                BookingStateMachine.transition(booking.getStatus(), BookingStatus.IN_PROGRESS);
        if (transition.isFailure()) {
            return Result.failure(transition.error());
        }

        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setStartedAt(Instant.now());
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingStarted(booking.getId(), booking.getCustomerId(), booking.getDriverId()));
        return Result.success(toSummary(booking));
    }

    /**
     * Self-service - driver marks drop-off complete. finalFare = fareEstimate
     * (no real trip-distance tracking exists yet to base a genuinely
     * different figure on - see FareCalculator's Javadoc).
     */
    @Transactional
    public Result<BookingSummary, BookingError> completeTrip(UUID bookingId) {
        Optional<BookingEntity> found = bookingRepository.findById(bookingId);
        if (found.isEmpty()) {
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

    /** Self-service - either participant (customer or the assigned driver) can cancel pre-IN_PROGRESS. Checked by the controller. */
    @Transactional
    public Result<BookingSummary, BookingError> cancelBooking(UUID bookingId) {
        Optional<BookingEntity> found = bookingRepository.findById(bookingId);
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
        bookingRepository.save(booking);

        eventPublisher.publish(new BookingCancelled(booking.getId(), booking.getCustomerId(), booking.getDriverId()));
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
                        b -> Result.success(new BookingParticipants(b.getCustomerId(), b.getDriverId())))
                .orElseGet(() -> Result.failure(BookingError.BOOKING_NOT_FOUND));
    }

    public List<BookingSummary> listForCustomer(UUID customerId) {
        return bookingRepository.findByCustomerId(customerId).stream().map(this::toSummary).toList();
    }

    public List<BookingSummary> listForDriver(UUID driverId) {
        return bookingRepository.findByDriverId(driverId).stream().map(this::toSummary).toList();
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
                booking.getCancelledAt()
        );
    }
}
