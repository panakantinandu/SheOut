package com.sheout.admin.internal;

import com.sheout.auth.AccountRole;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingOpsFacts;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingSummary;
import com.sheout.booking.BookingType;
import com.sheout.booking.GeoAddress;
import com.sheout.booking.ServiceHoursApi.ServiceHoursSettings;
import com.sheout.booking.ServiceHoursApi.ServiceStatus;
import com.sheout.dispatch.DriverLocation;
import com.sheout.driververification.ShiftCheckState;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.payments.PaymentSummary;
import com.sheout.payouts.WalletSummary;
import com.sheout.ratings.Rating;
import com.sheout.users.EmergencyContact;
import com.sheout.users.OnlineStatus;
import com.sheout.users.SavedPlace;
import com.sheout.users.TrustStats;
import com.sheout.users.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The console's three "everything about it" views - one person, one trip,
 * and what is happening right now - composed in AdminOpsService from other
 * modules' public interfaces. Records only; nothing here is stored.
 */
public final class OpsViews {

    private OpsViews() {
    }

    /**
     * One person, in full. Rider-only fields are null for a partner and the
     * partner block is null for a rider, so the console never has to guess
     * which half applies.
     */
    public record AccountDetail(
            AccountOpsRow account,
            Instant lastActiveAt,
            LocalDate dateOfBirth,
            String profilePhotoUrl,
            boolean profileComplete,
            boolean documentSubmitted,
            Instant documentSubmittedAt,
            String verificationRejectionReason,
            SavedPlace home,
            SavedPlace work,
            List<EmergencyContact> emergencyContacts,
            BigDecimal riderWalletBalance,
            PartnerFacts partner,
            List<TripRow> recentTrips,
            List<SosAlertSummary> sosAlerts
    ) {
    }

    /**
     * The PAN is masked to its last four: an operator confirming a partner on
     * the phone needs to match it, not to copy it.
     */
    public record PartnerFacts(
            VehicleType vehicleType,
            String vehicleRegistrationNumber,
            String panMasked,
            OnlineStatus onlineStatus,
            DriverLocation lastLocation,
            boolean onTrip,
            WalletSummary wallet,
            boolean payoutBankOnFile,
            String payoutAccountMasked,
            String payoutIfsc,
            String payoutUpi,
            ShiftCheckState shiftCheck,
            int implausibleJumpsToday
    ) {
    }

    /** One trip in a person's history. side is which end of it they were on. */
    public record TripRow(
            UUID bookingId,
            BookingStatus status,
            BookingType type,
            BookingCategory category,
            AccountRole side,
            String counterpartName,
            String pickup,
            String drop,
            BigDecimal fareEstimate,
            BigDecimal finalFare,
            Instant requestedAt
    ) {
    }

    /** Someone on a trip, as the trip page shows them. Vehicle fields are null for the rider. */
    public record Party(
            UUID accountId,
            String name,
            String phone,
            AccountRole role,
            VehicleType vehicleType,
            String vehicleRegistrationNumber,
            TrustStats trustStats,
            boolean blocked
    ) {
    }

    /**
     * One trip, in full. cancelledBySide says which end cancelled - RIDER,
     * PARTNER, or null for neither (no partner found, or operations).
     * partnerNow is her live position while the trip is live, else null.
     */
    public record BookingDetail(
            BookingSummary booking,
            BookingOpsFacts facts,
            String cancelledBySide,
            Party rider,
            Party partner,
            PaymentSummary payment,
            List<SosAlertSummary> sosAlerts,
            List<DriverLocation> trail,
            DriverLocation partnerNow,
            Rating riderRating,
            Rating partnerRating,
            List<com.sheout.booking.TripAlertsApi.TripAlert> alerts,
            List<com.sheout.booking.BookingEvent> events
    ) {
    }

    /** What is happening now. Fixes older than staleAfterSeconds are flagged, not hidden. */
    public record LiveOps(
            Instant at,
            ServiceStatus service,
            long staleAfterSeconds,
            List<LivePartner> partners,
            List<LiveTrip> trips
    ) {
    }

    public record LivePartner(
            UUID accountId,
            String name,
            String phone,
            VehicleType vehicleType,
            String vehicleRegistrationNumber,
            double lat,
            double lng,
            Instant lastFixAt,
            UUID tripId
    ) {
    }

    public record LiveTrip(
            UUID bookingId,
            BookingStatus status,
            BookingCategory category,
            UUID riderId,
            String riderName,
            String riderPhone,
            UUID partnerId,
            String partnerName,
            String partnerPhone,
            GeoAddress pickup,
            GeoAddress drop,
            BigDecimal fareEstimate,
            Instant requestedAt,
            Instant statusSince,
            DriverLocation partnerNow,
            boolean sosActive,
            List<com.sheout.booking.TripAlertsApi.TripAlert> alerts
    ) {
    }

    /** The console's service-hours panel: the settings, what they mean now, and who changed them. */
    public record ServiceHoursView(
            ServiceHoursSettings settings,
            ServiceStatus status,
            String updatedByPhone,
            String pausedByPhone,
            List<ServiceHoursChangeRow> changes
    ) {
    }

    public record ServiceHoursChangeRow(String byPhone, String summary, Instant at) {
    }
}
