package com.sheout.booking.internal;

import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingCancelled;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingType;
import com.sheout.booking.CancellationReason;
import com.sheout.booking.internal.fare.FareCalculator;
import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.geo.ServiceArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Whose cancellation it is is decided from the evidence, not from who pressed the button. */
class BookingCancellationJudgementTest {

    private static final double PICKUP_LAT = 17.3800, PICKUP_LNG = 78.4800;

    private final BookingRepository repository = mock(BookingRepository.class);
    private final DriverLocationApi locations = mock(DriverLocationApi.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private final UUID bookingId = UUID.randomUUID();
    private final UUID rider = UUID.randomUUID();
    private final UUID partner = UUID.randomUUID();
    private BookingService service;
    private BookingEntity booking;

    @BeforeEach
    void setUp() {
        service = new BookingService(repository, mock(VerificationApi.class), mock(FareCalculator.class), events,
                mock(AuthApi.class), mock(ServiceArea.class), "", 10, locations, 150, 30);
        booking = new BookingEntity(BookingType.RIDE, BookingCategory.BIKE, rider,
                new GeoAddressEmbeddable("pickup", PICKUP_LAT, PICKUP_LNG),
                new GeoAddressEmbeddable("drop", 17.4400, 78.3900), BigDecimal.TEN);
        when(repository.findLockedById(bookingId)).thenReturn(Optional.of(booking));
    }

    private void accepted(Duration ago) {
        booking.setDriverId(partner);
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setAcceptedAt(Instant.now().minus(ago));
    }

    private void partnerIs(double lat, double lng) {
        when(locations.findLocation(partner)).thenReturn(Optional.of(new DriverLocation(lat, lng, Instant.now())));
    }

    private BookingCancelled published() {
        ArgumentCaptor<BookingCancelled> c = ArgumentCaptor.forClass(BookingCancelled.class);
        verify(events).publish(c.capture());
        return c.getValue();
    }

    @Test
    void cancellingASearchCountsAgainstNobody() {
        assertThat(service.cancelBooking(bookingId, rider, CancellationReason.CHANGE_OF_PLANS, null).isSuccess()).isTrue();
        assertThat(published().countsAgainst()).isNull();
    }

    @Test
    void aRiderChangingHerMindAfterAcceptCountsAgainstHer() {
        accepted(Duration.ofMinutes(3));
        partnerIs(17.40, 78.45);
        service.cancelBooking(bookingId, rider, CancellationReason.CHANGE_OF_PLANS, null);
        assertThat(published().countsAgainst()).isEqualTo(rider);
    }

    @Test
    void wrongVehicleIsNeverHeldAgainstHerAndReportsThePartner() {
        accepted(Duration.ofMinutes(8));
        service.cancelBooking(bookingId, rider, CancellationReason.WRONG_VEHICLE, null);
        BookingCancelled event = published();
        assertThat(event.countsAgainst()).isNull();
        assertThat(event.reported()).isEqualTo(partner);
    }

    @Test
    void aPartnerFeelingUnsafeReportsTheRiderAndIsNotCounted() {
        accepted(Duration.ofMinutes(8));
        service.cancelBooking(bookingId, partner, CancellationReason.SAFETY_CONCERN, null);
        BookingCancelled event = published();
        assertThat(event.countsAgainst()).isNull();
        assertThat(event.reported()).isEqualTo(rider);
    }

    @Test
    void aSafetyReportNeedsSomebodyToReport() {
        var result = service.cancelBooking(bookingId, rider, CancellationReason.IDENTITY_MISMATCH, null);
        assertThat(result.error()).isEqualTo(BookingError.CANCELLATION_REASON_NOT_ALLOWED);
        verify(events, never()).publish(any());
    }

    @Test
    void aRiderCannotGiveAPartnersReason() {
        accepted(Duration.ofMinutes(3));
        assertThat(service.cancelBooking(bookingId, rider, CancellationReason.CUSTOMER_NOT_AT_PICKUP, null).error())
                .isEqualTo(BookingError.CANCELLATION_REASON_NOT_ALLOWED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
    }

    @Test
    void aPartnerCannotReportHerOwnVehicle() {
        accepted(Duration.ofMinutes(3));
        assertThat(service.cancelBooking(bookingId, partner, CancellationReason.WRONG_VEHICLE, null).error())
                .isEqualTo(BookingError.CANCELLATION_REASON_NOT_ALLOWED);
    }

    @Test
    void riderNoShowMustBeClaimedFromThePickup() {
        accepted(Duration.ofMinutes(12));
        partnerIs(17.45, 78.40); // kilometres away
        assertThat(service.cancelBooking(bookingId, partner, CancellationReason.CUSTOMER_NOT_AT_PICKUP, null).error())
                .isEqualTo(BookingError.DRIVER_NOT_AT_PICKUP);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
    }

    @Test
    void riderNoShowFromThePickupCountsAgainstTheRider() {
        accepted(Duration.ofMinutes(12));
        partnerIs(PICKUP_LAT, PICKUP_LNG);
        service.cancelBooking(bookingId, partner, CancellationReason.CUSTOMER_NOT_AT_PICKUP, null);
        assertThat(published().countsAgainst()).isEqualTo(rider);
    }

    @Test
    void givingUpOnAPartnerWhoNeverCameIsNotHeldAgainstTheRider() {
        accepted(Duration.ofMinutes(25));
        partnerIs(17.45, 78.40);
        service.cancelBooking(bookingId, rider, CancellationReason.DRIVER_TAKING_TOO_LONG, null);
        assertThat(published().countsAgainst()).isNull();
    }

    @Test
    void impatienceAFewMinutesAfterAcceptStillCounts() {
        accepted(Duration.ofMinutes(4));
        partnerIs(17.45, 78.40);
        service.cancelBooking(bookingId, rider, CancellationReason.DRIVER_TAKING_TOO_LONG, null);
        assertThat(published().countsAgainst()).isEqualTo(rider);
    }

    /** The rider still needs her ride: the booking searches again, without this partner, and nothing is cancelled. */
    @Test
    void aPartnerDroppingATripSendsItBackToSearchWithoutHer() {
        accepted(Duration.ofMinutes(4));
        booking.setPickupOtp("4821");
        partnerIs(17.42, 78.46);

        var result = service.cancelBooking(bookingId, partner, CancellationReason.DRIVER_UNAVAILABLE, null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(booking.getDriverId()).isNull();
        assertThat(booking.getPickupOtp()).isNull();
        ArgumentCaptor<com.sheout.sharedkernel.event.DomainEvent> c = ArgumentCaptor.forClass(com.sheout.sharedkernel.event.DomainEvent.class);
        verify(events, org.mockito.Mockito.times(2)).publish(c.capture());
        assertThat(c.getAllValues()).noneMatch(e -> e instanceof BookingCancelled);
        assertThat(c.getAllValues()).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(
                com.sheout.booking.PartnerLeftBooking.class, left -> assertThat(left.driverId()).isEqualTo(partner)));
        assertThat(c.getAllValues()).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(
                com.sheout.booking.BookingRequested.class, again -> assertThat(again.excludedDriverId()).isEqualTo(partner)));
    }

    /** A partner who will not go back to a rider she felt unsafe with ends the booking; nobody else is sent. */
    @Test
    void aPartnersSafetyConcernEndsTheBookingRatherThanSendingSomebodyElse() {
        accepted(Duration.ofMinutes(4));
        service.cancelBooking(bookingId, partner, CancellationReason.SAFETY_CONCERN, null);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
