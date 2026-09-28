package com.sheout.booking.internal;

import com.sheout.booking.BookingCategory;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.internal.fare.RouteEstimate;
import com.sheout.booking.internal.fare.RouteProvider;
import com.sheout.dispatch.NearbyPartnerApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteTimesTest {

    private final NearbyPartnerApi nearby = mock(NearbyPartnerApi.class);
    private final RouteProvider routes = mock(RouteProvider.class);
    private final GeoAddress pickup = new GeoAddress("Hitech City", 17.4483, 78.3915);
    private final Instant now = Instant.parse("2026-09-28T10:30:20Z");

    @SuppressWarnings("unchecked")
    private QuoteTimes times() {
        ObjectProvider<NearbyPartnerApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(nearby);
        return new QuoteTimes(provider, routes);
    }

    @Test
    void etaIsTheNearestPartnersRoadTimeAndDropIncludesTheWait() {
        when(nearby.nearestAvailable(anyDouble(), anyDouble(), any()))
                .thenReturn(Optional.of(new NearbyPartnerApi.NearbyPartner(17.45, 78.39, 1.2)));
        when(routes.route(any(), any())).thenReturn(new RouteEstimate(1.6, 3.4, RouteEstimate.Source.ROUTED));

        QuoteTimes.Times t = times().estimate(BookingCategory.BIKE, pickup, 18.2, now);

        // 3.4 min on the road + 1 to accept -> 5 (rounded up); drop = now + 5 + 18.2 min, up to the minute.
        assertThat(t.pickupEtaMinutes()).isEqualTo(5);
        assertThat(t.dropBy()).isEqualTo(Instant.parse("2026-09-28T10:54:00Z"));
    }

    @Test
    void withNobodyNearbyThereIsNoGuess() {
        when(nearby.nearestAvailable(anyDouble(), anyDouble(), any())).thenReturn(Optional.empty());

        QuoteTimes.Times t = times().estimate(BookingCategory.BIKE, pickup, 18.2, now);

        assertThat(t.pickupEtaMinutes()).isNull();
        assertThat(t.dropBy()).isNull();
    }

    @Test
    void aFailingLookupNeverRefusesTheQuote() {
        when(nearby.nearestAvailable(anyDouble(), anyDouble(), any())).thenThrow(new IllegalStateException("redis down"));

        assertThat(times().estimate(BookingCategory.PARCEL, pickup, 10, now).pickupEtaMinutes()).isNull();
    }
}
