package com.sheout.booking.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingType;
import com.sheout.booking.TripAlertRaised;
import com.sheout.booking.TripAlertsApi.Kind;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.dispatch.TripTrailApi;
import com.sheout.sharedkernel.cluster.ClusterLock;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A condition a person has checked on stays quiet for a while, then comes back if it still holds. */
class TripWatchServiceTest {

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final TripAlertRepository alerts = mock(TripAlertRepository.class);
    private final DriverLocationApi locations = mock(DriverLocationApi.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private TripWatchService service;
    private BookingEntity trip;

    @BeforeEach
    void setUp() {
        service = new TripWatchService(bookings, alerts, locations, mock(TripTrailApi.class), events, mock(ClusterLock.class),
                mock(PlatformTransactionManager.class), true, 45, 3, 8, 80, 1.5, 15, 30);
        trip = new BookingEntity(BookingType.RIDE, BookingCategory.BIKE, UUID.randomUUID(),
                new GeoAddressEmbeddable("a", 17.38, 78.48), new GeoAddressEmbeddable("b", 17.44, 78.39), BigDecimal.TEN);
        // Accepted an hour ago, never started, and her phone has never reported: stuck, and silent.
        trip.setDriverId(UUID.randomUUID());
        trip.setStatus(BookingStatus.ACCEPTED);
        trip.setAcceptedAt(Instant.now().minus(Duration.ofMinutes(60)));
        when(bookings.findByStatusIn(any())).thenReturn(List.of(trip));
        when(alerts.findByResolvedAtIsNullOrderByRaisedAtAsc()).thenReturn(List.of());
        when(alerts.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private TripAlertEntity checked(Kind kind, Duration ago) {
        TripAlertEntity a = new TripAlertEntity(trip.getId(), kind, "x", Instant.now().minus(Duration.ofHours(2)));
        a.resolve(Instant.now().minus(ago), UUID.randomUUID(), "called her");
        return a;
    }

    @Test
    void aNewConditionRaisesAnAlert() {
        when(alerts.findFirstByBookingIdAndKindAndResolvedByIsNotNullOrderByResolvedAtDesc(any(), any())).thenReturn(Optional.empty());
        service.runOnce();
        verify(events, org.mockito.Mockito.times(2)).publish(any(TripAlertRaised.class));
    }

    @Test
    void checkedRecentlyStaysQuiet() {
        when(alerts.findFirstByBookingIdAndKindAndResolvedByIsNotNullOrderByResolvedAtDesc(any(), eq(Kind.STUCK_ACCEPTED)))
                .thenReturn(Optional.of(checked(Kind.STUCK_ACCEPTED, Duration.ofMinutes(5))));
        when(alerts.findFirstByBookingIdAndKindAndResolvedByIsNotNullOrderByResolvedAtDesc(any(), eq(Kind.LOCATION_SILENT)))
                .thenReturn(Optional.of(checked(Kind.LOCATION_SILENT, Duration.ofMinutes(5))));
        service.runOnce();
        verify(events, never()).publish(any());
    }

    @Test
    void stillSoAfterTheSnoozeComesBack() {
        when(alerts.findFirstByBookingIdAndKindAndResolvedByIsNotNullOrderByResolvedAtDesc(any(), any()))
                .thenReturn(Optional.of(checked(Kind.STUCK_ACCEPTED, Duration.ofMinutes(40))));
        service.runOnce();
        verify(events, org.mockito.Mockito.atLeastOnce()).publish(any(TripAlertRaised.class));
    }
}
