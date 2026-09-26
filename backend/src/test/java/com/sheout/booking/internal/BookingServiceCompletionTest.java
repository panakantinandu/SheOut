package com.sheout.booking.internal;

import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingType;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.internal.fare.FareCalculator;
import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.geo.ServiceArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BookingServiceCompletionTest {

    private final BookingRepository repository = mock(BookingRepository.class);
    private final DriverLocationApi locations = mock(DriverLocationApi.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private final UUID bookingId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private BookingEntity booking;
    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(
                repository,
                mock(VerificationApi.class),
                mock(FareCalculator.class),
                events,
                mock(AuthApi.class),
                mock(ServiceArea.class),
                "",
                10,
                locations,
                100,
                30);
        booking = new BookingEntity(
                BookingType.RIDE,
                BookingCategory.BIKE,
                UUID.randomUUID(),
                new GeoAddressEmbeddable("pickup", 17.3800, 78.4800),
                new GeoAddressEmbeddable("drop", 17.3850, 78.4867),
                BigDecimal.TEN);
        booking.setDriverId(driverId);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        when(repository.findLockedById(bookingId)).thenReturn(Optional.of(booking));
    }

    @Test
    void assignedDriverAtDropoffCanComplete() {
        when(locations.findLocation(driverId)).thenReturn(Optional.of(
                new DriverLocation(17.3850, 78.4867, Instant.now())));

        var result = service.completeTrip(bookingId, driverId);

        assertTrue(result.isSuccess());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        verify(events).publish(any());
    }

    @Test
    void driverOutsideDropoffCannotCompleteAndNoCompletionIsPersisted() {
        when(locations.findLocation(driverId)).thenReturn(Optional.of(
                new DriverLocation(17.3900, 78.4867, Instant.now())));

        var result = service.completeTrip(bookingId, driverId);

        assertEquals(BookingError.DRIVER_NOT_AT_DROP_OFF, result.error());
        assertEquals(BookingStatus.IN_PROGRESS, booking.getStatus());
        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void unauthorizedDriverCannotComplete() {
        when(locations.findLocation(driverId)).thenReturn(Optional.of(
                new DriverLocation(17.3850, 78.4867, Instant.now())));

        var result = service.completeTrip(bookingId, UUID.randomUUID());

        assertEquals(BookingError.BOOKING_NOT_FOUND, result.error());
        verifyNoInteractions(locations, events);
    }

    @Test
    void alreadyCompletedTripCannotBeCompletedAgain() {
        booking.setStatus(BookingStatus.COMPLETED);

        var result = service.completeTrip(bookingId, driverId);

        assertEquals(BookingError.INVALID_STATE_TRANSITION, result.error());
        verifyNoInteractions(locations, events);
    }

    @Test
    void riderCanEndHerTripShortOfTheDropAtTheQuotedFare() {
        var result = service.endTripAtRidersRequest(bookingId, booking.getCustomerId());

        assertTrue(result.isSuccess());
        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(BigDecimal.TEN, booking.getFinalFare());
        // Her word is enough - no GPS is consulted.
        verifyNoInteractions(locations);
        verify(events).publish(any());
    }

    @Test
    void nobodyButTheRiderCanEndHerTrip() {
        var result = service.endTripAtRidersRequest(bookingId, driverId);

        assertEquals(BookingError.BOOKING_NOT_FOUND, result.error());
        assertEquals(BookingStatus.IN_PROGRESS, booking.getStatus());
        verifyNoInteractions(events);
    }

    @Test
    void aTripThatHasNotStartedCannotBeEndedByTheRider() {
        booking.setStatus(BookingStatus.ACCEPTED);

        var result = service.endTripAtRidersRequest(bookingId, booking.getCustomerId());

        assertEquals(BookingError.INVALID_STATE_TRANSITION, result.error());
        verifyNoInteractions(events);
    }

    @Test
    void secondCompletionRequestIsIdempotentlyRejected() {
        when(locations.findLocation(driverId)).thenReturn(Optional.of(
                new DriverLocation(17.3850, 78.4867, Instant.now())));

        assertTrue(service.completeTrip(bookingId, driverId).isSuccess());
        var second = service.completeTrip(bookingId, driverId);

        assertEquals(BookingError.INVALID_STATE_TRANSITION, second.error());
        verify(events, times(1)).publish(any());
    }
}
