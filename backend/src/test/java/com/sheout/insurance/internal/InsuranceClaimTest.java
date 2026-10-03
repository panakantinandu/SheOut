package com.sheout.insurance.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingSummary;
import com.sheout.insurance.PolicyKind;
import com.sheout.insurance.TripCover;
import com.sheout.sharedkernel.Result;
import com.sheout.support.CreateTicketCommand;
import com.sheout.support.SupportApi;
import com.sheout.support.SupportTicketCategory;
import com.sheout.support.SupportTicketSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An accident on a trip becomes a high-priority ticket linked to the trip and its cover, with the insurer's own steps. */
class InsuranceClaimTest {

    private final SupportApi support = mock(SupportApi.class);
    private final BookingApi bookings = mock(BookingApi.class);
    private final InsuranceService insurance = mock(InsuranceService.class);
    private final TripCoverageRepository coverages = mock(TripCoverageRepository.class);
    private final TripCoverageClaimRepository claims = mock(TripCoverageClaimRepository.class);
    private final InsuranceClaimService service = new InsuranceClaimService(support, bookings, insurance, coverages, claims);
    private final UUID trip = UUID.randomUUID();
    private final UUID rider = UUID.randomUUID();
    private final UUID partner = UUID.randomUUID();

    private void tripIs(BookingStatus status) {
        BookingSummary b = mock(BookingSummary.class);
        when(b.customerId()).thenReturn(rider);
        when(b.driverId()).thenReturn(partner);
        when(b.status()).thenReturn(status);
        when(bookings.findById(trip)).thenReturn(Optional.of(b));
    }

    @Test
    void aCoveredTripsClaimIsAHighPriorityTicketNamingThePolicy() {
        tripIs(BookingStatus.COMPLETED);
        TripCover cover = new TripCover(trip, PolicyKind.PASSENGER_TRIP, "Example General", "MP-1",
                new BigDecimal("500000"), null, "Call the claims line within 48 hours", "1800-000-000", null, null,
                Instant.now(), Instant.now());
        when(insurance.coverForTrip(trip)).thenReturn(Optional.of(cover));
        SupportTicketSummary ticket = mock(SupportTicketSummary.class);
        UUID ticketId = UUID.randomUUID();
        when(ticket.id()).thenReturn(ticketId);
        when(support.createTicket(any())).thenReturn(Result.success(ticket));
        when(coverages.findByBookingId(trip)).thenReturn(Optional.empty());

        var result = service.raise(trip, rider, AccountRole.CUSTOMER, "A car hit us at the signal. I hurt my arm.");

        assertThat(result.value().ticketId()).isEqualTo(ticketId);
        assertThat(result.value().cover().claimsPhone()).isEqualTo("1800-000-000");
        ArgumentCaptor<CreateTicketCommand> cmd = ArgumentCaptor.forClass(CreateTicketCommand.class);
        verify(support).createTicket(cmd.capture());
        assertThat(cmd.getValue().category()).isEqualTo(SupportTicketCategory.ACCIDENT_OR_INSURANCE_CLAIM);
        assertThat(cmd.getValue().linkedBookingId()).isEqualTo(trip);
        assertThat(cmd.getValue().description()).contains("policy MP-1");
        verify(claims).save(any());
        assertThat(com.sheout.support.SupportTicketPriority.forCategory(SupportTicketCategory.ACCIDENT_OR_INSURANCE_CLAIM))
                .isEqualTo(com.sheout.support.SupportTicketPriority.HIGH);
    }

    @Test
    void anUncoveredTripCanStillReportAnAccidentAndSaysSo() {
        tripIs(BookingStatus.IN_PROGRESS);
        when(insurance.coverForTrip(trip)).thenReturn(Optional.empty());
        SupportTicketSummary ticket = mock(SupportTicketSummary.class);
        when(ticket.id()).thenReturn(UUID.randomUUID());
        when(support.createTicket(any())).thenReturn(Result.success(ticket));
        when(coverages.findByBookingId(trip)).thenReturn(Optional.empty());

        var result = service.raise(trip, partner, AccountRole.DRIVER, "Skidded on gravel.");

        assertThat(result.value().cover()).isNull();
        ArgumentCaptor<CreateTicketCommand> cmd = ArgumentCaptor.forClass(CreateTicketCommand.class);
        verify(support).createTicket(cmd.capture());
        assertThat(cmd.getValue().description()).contains("no insurance cover on record");
    }

    @Test
    void notSomebodyElsesTripNorOneThatNeverStarted() {
        tripIs(BookingStatus.ACCEPTED);
        assertThat(service.raise(trip, UUID.randomUUID(), AccountRole.CUSTOMER, "x").error())
                .isEqualTo(InsuranceClaimService.ClaimError.TRIP_NOT_FOUND);
        assertThat(service.raise(trip, rider, AccountRole.CUSTOMER, "x").error())
                .isEqualTo(InsuranceClaimService.ClaimError.TRIP_NOT_STARTED);
        verify(support, never()).createTicket(any());
    }
}
