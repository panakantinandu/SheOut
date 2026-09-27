package com.sheout.booking.internal;

import com.sheout.auth.AuthApi;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingCompleted;
import com.sheout.booking.BookingError;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingType;
import com.sheout.booking.DestinationChangeDeclined;
import com.sheout.booking.DestinationChangeRequested;
import com.sheout.booking.DestinationChanged;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.internal.fare.FareCalculator;
import com.sheout.booking.internal.fare.FareQuote;
import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.sharedkernel.event.DomainEvent;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.geo.ServiceArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DestinationChangeServiceTest {

    private static final Instant START = Instant.parse("2026-09-27T10:00:00Z");
    private static final GeoAddress PICKUP = new GeoAddress("Ravindra Bharathi", 17.4065, 78.4757);
    private static final GeoAddress FAR_DROP = new GeoAddress("Far", 17.4965, 78.4757);   // ~10 km north
    private static final GeoAddress NEAR_DROP = new GeoAddress("Near", 17.4425, 78.4757); // ~4 km north

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final DestinationChangeRepository changes = mock(DestinationChangeRepository.class);
    private final FareCalculator fares = mock(FareCalculator.class);
    private final ServiceArea serviceArea = mock(ServiceArea.class);
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEventPublisher events = published::add;
    /** The rows the fake repository holds, oldest first. */
    private final List<DestinationChangeEntity> rows = new ArrayList<>();
    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();
    private Instant now = START.plusSeconds(300);
    private BookingEntity booking;
    private DestinationChangeService service;

    @BeforeEach
    void setUp() {
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        service = new DestinationChangeService(bookings, changes, fares, serviceArea, events,
                Duration.ofSeconds(120), 1, clock);

        booking = new BookingEntity(BookingType.RIDE, BookingCategory.BIKE, customerId,
                GeoAddressEmbeddable.from(PICKUP), GeoAddressEmbeddable.from(FAR_DROP), new BigDecimal("120.00"));
        booking.recordQuotedDistance(new BigDecimal("10.00"), true);
        booking.setDriverId(driverId);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setStartedAt(START);
        when(bookings.findLockedById(bookingId)).thenReturn(Optional.of(booking));
        when(bookings.findById(bookingId)).thenReturn(Optional.of(booking));
        when(serviceArea.covers(anyDouble(), anyDouble())).thenReturn(true);
        when(fares.quote(eq(BookingCategory.BIKE), any(), any())).thenAnswer(inv -> {
            GeoAddress drop = inv.getArgument(2);
            return drop.equals(NEAR_DROP) ? quote("58.00", 4.0) : quote("120.00", 10.0);
        });

        when(changes.save(any())).thenAnswer(inv -> keep(inv.getArgument(0)));
        when(changes.saveAndFlush(any())).thenAnswer(inv -> keep(inv.getArgument(0)));
        when(changes.findFirstByBookingIdOrderByCreatedAtDesc(bookingId))
                .thenAnswer(inv -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(rows.size() - 1)));
        when(changes.findByBookingIdAndStatus(eq(bookingId), any()))
                .thenAnswer(inv -> rows.stream().filter(r -> r.getStatus() == inv.getArgument(1)).toList());
        when(changes.countByBookingIdAndStatusIn(eq(bookingId), anyCollection()))
                .thenAnswer(inv -> rows.stream().filter(r -> ((Collection<?>) inv.getArgument(1)).contains(r.getStatus())).count());
    }

    private DestinationChangeEntity keep(DestinationChangeEntity row) {
        if (!rows.contains(row)) rows.add(row);
        return row;
    }

    private static FareQuote quote(String amount, double km) {
        return new FareQuote(new BigDecimal(amount), km, km * 3, true, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, false);
    }

    @Test
    void askingPricesTheNewDropFromThePickupAndChangesNothingYet() {
        var result = service.request(bookingId, customerId, NEAR_DROP, new BigDecimal("58.00"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().status()).isEqualTo(DestinationChangeEntity.Status.PENDING);
        assertThat(result.value().oldFare()).isEqualByComparingTo("120.00");
        assertThat(result.value().newFare()).isEqualByComparingTo("58.00");
        assertThat(result.value().secondsLeft()).isEqualTo(120);
        // The trip itself is untouched until the partner says yes.
        assertThat(booking.getDrop().getLabel()).isEqualTo("Far");
        assertThat(booking.getFareEstimate()).isEqualByComparingTo("120.00");
        assertThat(published).singleElement().isInstanceOf(DestinationChangeRequested.class);
    }

    @Test
    void acceptingMovesTheDropFareAndQuotedDistanceTogether() {
        service.request(bookingId, customerId, NEAR_DROP, null);
        now = now.plusSeconds(30);

        var result = service.answer(bookingId, driverId, true);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().status()).isEqualTo(DestinationChangeEntity.Status.ACCEPTED);
        assertThat(booking.getDrop().getLabel()).isEqualTo("Near");
        assertThat(booking.getFareEstimate()).isEqualByComparingTo("58.00");
        assertThat(booking.getQuotedDistanceKm()).isEqualByComparingTo("4.00");
        assertThat(booking.getOriginalQuotedDistanceKm()).isEqualByComparingTo("10.00");
        assertThat(booking.getDestinationChangedAt()).isEqualTo(now);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        DestinationChanged changed = (DestinationChanged) published.get(1);
        assertThat(changed.oldDrop().label()).isEqualTo("Far");
        assertThat(changed.newDrop().label()).isEqualTo("Near");
        assertThat(changed.newFare()).isEqualByComparingTo("58.00");
    }

    @Test
    void theTripEndsAtTheNewDropAndIsChargedTheAgreedFare() {
        DriverLocationApi locations = mock(DriverLocationApi.class);
        BookingService bookingService = new BookingService(bookings, mock(VerificationApi.class), fares, events,
                mock(AuthApi.class), serviceArea, "", 10, locations, 150, 30);
        service.request(bookingId, customerId, NEAR_DROP, null);
        service.answer(bookingId, driverId, true);
        // She stops at the new drop: no reason is asked, because it is the drop.
        when(locations.findLocation(driverId)).thenReturn(Optional.of(new DriverLocation(NEAR_DROP.lat(), NEAR_DROP.lng(), Instant.now())));

        var result = bookingService.completeTrip(bookingId, driverId);

        assertThat(result.isSuccess()).isTrue();
        assertThat(booking.getFinalFare()).isEqualByComparingTo("58.00");
        assertThat(booking.getDropDeviationReason()).isNull();
        BookingCompleted completed = (BookingCompleted) published.get(published.size() - 1);
        assertThat(completed.finalFare()).isEqualByComparingTo("58.00");
    }

    @Test
    void decliningLeavesTheTripAsBookedAndTellsHer() {
        service.request(bookingId, customerId, NEAR_DROP, null);

        var result = service.answer(bookingId, driverId, false);

        assertThat(result.value().status()).isEqualTo(DestinationChangeEntity.Status.DECLINED);
        assertThat(booking.getDrop().getLabel()).isEqualTo("Far");
        assertThat(booking.getFareEstimate()).isEqualByComparingTo("120.00");
        assertThat(booking.getQuotedDistanceKm()).isEqualByComparingTo("10.00");
        assertThat(booking.getDestinationChangedAt()).isNull();
        DestinationChangeDeclined declined = (DestinationChangeDeclined) published.get(1);
        assertThat(declined.drop().label()).isEqualTo("Far");
        assertThat(declined.fare()).isEqualByComparingTo("120.00");
    }

    @Test
    void onceAnsweredSheCannotAskAgainOnThisTrip() {
        service.request(bookingId, customerId, NEAR_DROP, null);
        service.answer(bookingId, driverId, false);

        assertThat(service.request(bookingId, customerId, NEAR_DROP, null).error())
                .isEqualTo(BookingError.DESTINATION_CHANGE_LIMIT_REACHED);
        assertThat(service.state(bookingId).orElseThrow().canRequest()).isFalse();
    }

    @Test
    void oneQuestionAtATime() {
        service.request(bookingId, customerId, NEAR_DROP, null);

        assertThat(service.request(bookingId, customerId, NEAR_DROP, null).error())
                .isEqualTo(BookingError.DESTINATION_CHANGE_PENDING);
    }

    @Test
    void anUnansweredRequestRunsOutChangesNothingAndDoesNotUseUpHerChange() {
        service.request(bookingId, customerId, NEAR_DROP, null);
        now = now.plusSeconds(121);

        assertThat(service.state(bookingId).orElseThrow().change().status()).isEqualTo(DestinationChangeEntity.Status.EXPIRED);
        assertThat(service.answer(bookingId, driverId, true).error()).isEqualTo(BookingError.DESTINATION_CHANGE_NOT_PENDING);
        assertThat(booking.getDrop().getLabel()).isEqualTo("Far");
        assertThat(booking.getFareEstimate()).isEqualByComparingTo("120.00");
        // Nobody said no, so she may ask again.
        assertThat(service.state(bookingId).orElseThrow().canRequest()).isTrue();
        assertThat(service.request(bookingId, customerId, NEAR_DROP, null).isSuccess()).isTrue();
    }

    @Test
    void aRequestLeftWaitingWhenTheTripEndsCannotBeAcceptedAfterwards() {
        service.request(bookingId, customerId, NEAR_DROP, null);
        booking.setStatus(BookingStatus.COMPLETED);

        assertThat(service.state(bookingId).orElseThrow().change().status()).isEqualTo(DestinationChangeEntity.Status.EXPIRED);
        assertThat(service.answer(bookingId, driverId, true).error()).isEqualTo(BookingError.DESTINATION_CHANGE_NOT_PENDING);
        assertThat(booking.getDrop().getLabel()).isEqualTo("Far");
    }

    @Test
    void theSamePlaceIsNotAChange() {
        GeoAddress fiftyMetresOver = new GeoAddress("Gate", FAR_DROP.lat() + 0.00045, FAR_DROP.lng());
        assertThat(service.request(bookingId, customerId, fiftyMetresOver, null).error())
                .isEqualTo(BookingError.DESTINATION_UNCHANGED);
    }

    @Test
    void aFareSheWasNotShownIsNeverSent() {
        assertThat(service.request(bookingId, customerId, NEAR_DROP, new BigDecimal("55.00")).error())
                .isEqualTo(BookingError.DESTINATION_FARE_CHANGED);
        assertThat(rows).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void onlyDuringTheTripAndOnlyByHerAndHerPartner() {
        assertThat(service.request(bookingId, UUID.randomUUID(), NEAR_DROP, null).error()).isEqualTo(BookingError.BOOKING_NOT_FOUND);
        service.request(bookingId, customerId, NEAR_DROP, null);
        assertThat(service.answer(bookingId, UUID.randomUUID(), true).error()).isEqualTo(BookingError.BOOKING_NOT_FOUND);

        booking.setStatus(BookingStatus.ACCEPTED);
        rows.clear();
        assertThat(service.request(bookingId, customerId, NEAR_DROP, null).error()).isEqualTo(BookingError.INVALID_STATE_TRANSITION);
    }

    @Test
    void outsideTheServiceAreaIsRefused() {
        when(serviceArea.covers(anyDouble(), anyDouble())).thenReturn(false);
        assertThat(service.request(bookingId, customerId, NEAR_DROP, null).error()).isEqualTo(BookingError.OUTSIDE_SERVICE_AREA);
    }

    /**
     * The route check after an agreed change: she drove the 4 km to the new
     * drop, on a trip first quoted at 10 km. Measured against the original
     * quote that is 60% short and would be flagged; measured against the
     * route both of them agreed to, it is exactly the trip.
     */
    @Test
    void theRouteCheckMeasuresAgainstTheAgreedRouteNotTheOriginal() {
        List<DriverLocation> trail = northward(4.0);
        Instant endedAt = trail.get(trail.size() - 1).recordedAt().plusSeconds(2);
        TripRouteChecker checker = new TripRouteChecker(id -> trail, 25, 0.5);

        checker.check(booking, endedAt);
        assertThat(booking.getRouteFlaggedAt()).as("without the change, a 4 km drive on a 10 km quote").isNotNull();

        now = START.plusSeconds(60);
        service.request(bookingId, customerId, NEAR_DROP, null);
        service.answer(bookingId, driverId, true);
        checker.check(booking, endedAt);

        assertThat(booking.getActualDistanceKm().doubleValue()).isBetween(3.9, 4.1);
        assertThat(booking.getRouteFlaggedAt()).isNull();
    }

    /** A drive due north from the pickup at ~25 km/h, one report every 7 s. */
    private static List<DriverLocation> northward(double km) {
        List<DriverLocation> points = new ArrayList<>();
        double metresPerReport = 25000.0 / 3600 * 7;
        int reports = (int) Math.ceil(km * 1000 / metresPerReport);
        for (int i = 0; i <= reports; i++) {
            double metres = Math.min(km * 1000, i * metresPerReport);
            points.add(new DriverLocation(PICKUP.lat() + metres / 111_000, PICKUP.lng(), START.plusSeconds(i * 7L)));
        }
        points.sort(Comparator.comparing(DriverLocation::recordedAt));
        return points;
    }
}
