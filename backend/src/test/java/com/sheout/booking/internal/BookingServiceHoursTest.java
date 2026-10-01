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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
                TripRouteChecker.withoutTrails(), mock(CampaignsApi.class), () -> open);
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
