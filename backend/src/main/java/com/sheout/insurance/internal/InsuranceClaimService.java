package com.sheout.insurance.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingApi;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingSummary;
import com.sheout.insurance.TripCover;
import com.sheout.sharedkernel.Result;
import com.sheout.support.CreateTicketCommand;
import com.sheout.support.SupportApi;
import com.sheout.support.SupportError;
import com.sheout.support.SupportTicketCategory;
import com.sheout.support.SupportTicketSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * "Report an accident / make a claim" on a trip that started.
 * <p>
 * It raises a HIGH-priority support ticket linked to the trip - the existing
 * support module, so the operator who picks it up has the trip, the people
 * and the thread in one place - and records it beside the trip's cover. What
 * comes back is the insurer's own claim steps and phone number. SheOut helps
 * her through it; the insurer decides the claim, and nothing here suggests
 * otherwise.
 * <p>
 * A trip with no cover can still report an accident: somebody may be hurt,
 * and that is a support matter whether or not there is a policy to claim on.
 * The answer then simply carries no insurer.
 * <p>
 * Its own bean rather than part of InsuranceService because it needs support
 * and booking, and booking needs InsuranceService: keeping them apart keeps
 * that from being a cycle.
 */
@Service
public class InsuranceClaimService {

    public enum ClaimError {
        /** Not her trip, or no such trip - the same answer, so neither is revealed. */
        TRIP_NOT_FOUND,
        /** A trip that never started has nothing to claim on. */
        TRIP_NOT_STARTED,
        DESCRIPTION_REQUIRED
    }

    public record ClaimResult(UUID ticketId, TripCover cover) {
    }

    private final SupportApi support;
    private final BookingApi bookings;
    private final InsuranceService insurance;
    private final TripCoverageRepository coverages;
    private final TripCoverageClaimRepository claims;

    InsuranceClaimService(SupportApi support, BookingApi bookings, InsuranceService insurance,
                          TripCoverageRepository coverages, TripCoverageClaimRepository claims) {
        this.support = support;
        this.bookings = bookings;
        this.insurance = insurance;
        this.coverages = coverages;
        this.claims = claims;
    }

    @Transactional
    public Result<ClaimResult, ClaimError> raise(UUID bookingId, UUID accountId, AccountRole role, String description) {
        if (description == null || description.isBlank()) {
            return Result.failure(ClaimError.DESCRIPTION_REQUIRED);
        }
        Optional<BookingSummary> trip = bookings.findById(bookingId)
                .filter(b -> accountId.equals(b.customerId()) || accountId.equals(b.driverId()));
        if (trip.isEmpty()) {
            return Result.failure(ClaimError.TRIP_NOT_FOUND);
        }
        BookingStatus status = trip.get().status();
        if (status != BookingStatus.IN_PROGRESS && status != BookingStatus.COMPLETED) {
            return Result.failure(ClaimError.TRIP_NOT_STARTED);
        }
        Optional<TripCover> cover = insurance.coverForTrip(bookingId);
        String subject = cover.isPresent() ? "Accident on a trip - insurance claim" : "Accident on a trip";
        String body = description.trim();
        if (cover.isPresent()) {
            TripCover c = cover.get();
            body = body + "\n\nCover on this trip: " + c.insurerName() + ", policy " + c.policyNumber()
                    + ", sum insured " + rupees(c.sumInsured()) + ".";
        } else {
            body = body + "\n\nThis trip had no insurance cover on record.";
        }
        if (body.length() > 2000) {
            body = body.substring(0, 2000);
        }
        Result<SupportTicketSummary, SupportError> ticket = support.createTicket(new CreateTicketCommand(
                accountId, role, SupportTicketCategory.ACCIDENT_OR_INSURANCE_CLAIM, subject, body, bookingId));
        if (ticket.isFailure()) {
            return Result.failure(ClaimError.TRIP_NOT_FOUND);
        }
        UUID coverageId = coverages.findByBookingId(bookingId).map(TripCoverageEntity::getId).orElse(null);
        claims.save(new TripCoverageClaimEntity(bookingId, coverageId, ticket.value().id(), accountId));
        return Result.success(new ClaimResult(ticket.value().id(), cover.orElse(null)));
    }

    private static String rupees(BigDecimal amount) {
        return amount == null ? "-" : "Rs " + NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN")).format(amount);
    }
}
