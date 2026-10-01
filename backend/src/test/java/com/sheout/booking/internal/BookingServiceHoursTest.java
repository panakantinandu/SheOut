package com.sheout.booking.internal;

import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingType;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.RequestBookingCommand;
import com.sheout.booking.internal.fare.FareCalculator;
import com.sheout.campaigns.CampaignsApi;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.geo.ServiceArea;
import org.junit.jupiter.api.Test;

import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.booking.internal.fare.FareQuote;
import com.sheout.campaigns.PromoApplication;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** A closed service refuses a new booking before anything is checked, priced or saved. */
class BookingServiceHoursTest {

    private final BookingRepository repository = mock(BookingRepository.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private final FareCalculator fares = mock(FareCalculator.class);
    private final AuthApi auth = mock(AuthApi.class);

    private BookingService service(boolean open) {
        return new BookingService(repository, mock(VerificationApi.class), fares, events, auth,
                mock(ServiceArea.class), "", 10, mock(DriverLocationApi.class), 150, 30, 150,
                TripRouteChecker.withoutTrails(), mock(CampaignsApi.class), BookingWindow.fixed(open, null));
    }

    /** Everything before the hours check passes: in the area, a phone, verified, nothing owed. */
    private BookingService readyToBook(UUID customer, Instant finishBy) {
        ServiceArea area = mock(ServiceArea.class);
        when(area.covers(anyDouble(), anyDouble())).thenReturn(true);
        when(auth.findAccount(customer)).thenReturn(Optional.of(
                new AccountSummary(customer, "+919000000101", null, AccountRole.CUSTOMER, Instant.now(), false)));
        VerificationApi verification = mock(VerificationApi.class);
        when(verification.findByAccountId(customer)).thenReturn(Optional.of(new VerificationSummary(
                customer, AccountRole.CUSTOMER, VerificationStatus.VERIFIED, VerificationStatus.PENDING, true, null,
                Instant.now(), Instant.now())));
        // A 50-minute ride.
        when(fares.quote(any(), any(), any())).thenReturn(new FareQuote(new BigDecimal("400.00"), 30, 50, true,
                BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, false));
        CampaignsApi campaigns = mock(CampaignsApi.class);
        when(campaigns.reserveDiscount(any(), any(), any())).thenReturn(PromoApplication.none());
        return new BookingService(repository, verification, fares, events, auth, area, "", 10,
                mock(DriverLocationApi.class), 150, 30, 150, TripRouteChecker.withoutTrails(), campaigns,
                BookingWindow.fixed(true, finishBy));
    }

    private static RequestBookingCommand trip(UUID customer) {
        return new RequestBookingCommand(customer, BookingType.RIDE, BookingCategory.BIKE,
                new GeoAddress("a", 17.38, 78.48), new GeoAddress("b", 17.10, 79.20));
    }

    /** 15 minutes to arrive plus a 50-minute ride does not fit in the 10 minutes before the cut-off. */
    @Test
    void aLongTripNearClosingIsRefused() {
        UUID customer = UUID.randomUUID();
        var result = readyToBook(customer, Instant.now().plus(Duration.ofMinutes(10))).requestBooking(trip(customer));

        assertThat(result.error()).isEqualTo(BookingError.TRIP_ENDS_AFTER_HOURS);
        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void theSameTripWithTimeToSpareIsBooked() {
        UUID customer = UUID.randomUUID();
        var result = readyToBook(customer, Instant.now().plus(Duration.ofMinutes(90))).requestBooking(trip(customer));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void openAtAllHoursHasNoFinishLimit() {
        UUID customer = UUID.randomUUID();
        var result = readyToBook(customer, null).requestBooking(trip(customer));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void closedServiceRefusesWithServiceClosedAndTouchesNothing() {
        var result = service(false).requestBooking(new RequestBookingCommand(UUID.randomUUID(), BookingType.RIDE,
                BookingCategory.BIKE, new GeoAddress("a", 17.38, 78.48), new GeoAddress("b", 17.44, 78.39)));

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(BookingError.SERVICE_CLOSED);
        verifyNoInteractions(fares, auth);
        verify(repository, never()).save(any());
        verifyNoInteractions(events);
    }

    /** Open, the request goes on to the usual checks - here the service area, which the mock says no to. */
    @Test
    void openServiceGoesOnToTheUsualChecks() {
        var result = service(true).requestBooking(new RequestBookingCommand(UUID.randomUUID(), BookingType.RIDE,
                BookingCategory.BIKE, new GeoAddress("a", 17.38, 78.48), new GeoAddress("b", 17.44, 78.39)));

        assertThat(result.error()).isEqualTo(BookingError.OUTSIDE_SERVICE_AREA);
    }
}
