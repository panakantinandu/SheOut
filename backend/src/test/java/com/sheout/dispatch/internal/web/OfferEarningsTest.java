package com.sheout.dispatch.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingSummary;
import com.sheout.dispatch.internal.DispatchService;
import com.sheout.driververification.ShiftCheckApi;
import com.sheout.payments.PaymentApi;
import com.sheout.ratings.RatingsApi;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.users.CustomerProfileApi;
import com.sheout.users.DriverProfileApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A partner's offer says what she earns from the trip, worked out by payments, before she accepts. */
class OfferEarningsTest {

    private final DispatchService dispatch = mock(DispatchService.class);
    private final BookingApi bookings = mock(BookingApi.class);
    private final PaymentApi payments = mock(PaymentApi.class);
    private final DispatchController controller = new DispatchController(dispatch, bookings,
            mock(DriverProfileApi.class), mock(RatingsApi.class), mock(CustomerProfileApi.class),
            mock(RateLimiter.class), mock(ShiftCheckApi.class), payments);
    private final UUID driver = UUID.randomUUID();
    private final UUID booking = UUID.randomUUID();

    @AfterEach
    void clear() {
        CurrentAccountContext.clear();
    }

    @Test
    @DisplayName("the offer carries her earnings and the commission beside the fare")
    void offerCarriesHerEarnings() {
        BookingSummary b = mock(BookingSummary.class);
        when(b.fareEstimate()).thenReturn(new BigDecimal("107.00"));
        when(b.category()).thenReturn(BookingCategory.BIKE);
        when(dispatch.findActiveOffer(driver)).thenReturn(Optional.of(booking));
        when(bookings.findById(booking)).thenReturn(Optional.of(b));
        when(payments.partnerShareOf(new BigDecimal("107.00"))).thenReturn(new BigDecimal("87.74"));
        when(payments.commissionPercent()).thenReturn(new BigDecimal("18.00"));
        CurrentAccountContext.set(new CurrentAccount(driver, AccountRole.DRIVER, UUID.randomUUID()));

        DispatchController.OfferResponse offer = controller.getMyOffer().getBody();

        assertEquals(new BigDecimal("107.00"), offer.fareEstimate());
        assertEquals(new BigDecimal("87.74"), offer.partnerEarnings());
        assertEquals(new BigDecimal("18.00"), offer.commissionPercent());
    }

    @Test
    @DisplayName("a booking that has vanished still shows the offer, with no earnings invented")
    void vanishedBookingHasNoEarnings() {
        when(dispatch.findActiveOffer(driver)).thenReturn(Optional.of(booking));
        when(bookings.findById(booking)).thenReturn(Optional.empty());
        CurrentAccountContext.set(new CurrentAccount(driver, AccountRole.DRIVER, UUID.randomUUID()));

        DispatchController.OfferResponse offer = controller.getMyOffer().getBody();

        assertEquals(booking, offer.bookingId());
        assertNull(offer.partnerEarnings());
    }
}
