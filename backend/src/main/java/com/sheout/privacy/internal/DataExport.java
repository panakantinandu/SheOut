package com.sheout.privacy.internal;

import com.sheout.auth.AccountRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything SheOut holds about one account, in the shape the account holder
 * downloads.
 * <p>
 * The rule for what goes in: this person's own data, and nobody else's.
 * Where a record is shared with another person - a trip has two people on it
 * - the other person appears only as the name the app already showed this
 * person at the time. Their phone number, their account id, their documents
 * and their ratings are theirs and are never included. Ratings this person
 * received appear as the average and count the app shows, not individual
 * scores, because who rated whom is the other person's data too.
 * <p>
 * No document or photo URLs: those are short-lived storage links that would
 * expire in the file, and the export says whether one is on file instead.
 */
public record DataExport(
        Instant generatedAt,
        String notice,
        Account account,
        Profile profile,
        List<EmergencyContact> emergencyContacts,
        Verification verification,
        List<Trip> trips,
        RatingsReceived ratingsReceived,
        List<SupportTicket> supportTickets
) {

    public record Account(UUID accountId, AccountRole role, String phoneNumber, String email, Instant createdAt) {
    }

    /** Customer fields or driver fields, whichever this account is; the other half is null. */
    public record Profile(String name, String homeAddress, String workAddress, String vehicleType,
                          String vehicleRegistrationNumber, Boolean profilePhotoOnFile) {
    }

    /** Names and numbers this person saved for SOS. Theirs to see, because they entered them. */
    public record EmergencyContact(String name, String phoneNumber, String relationship) {
    }

    public record Verification(String identityCheck, String policeCheck, boolean identityDocumentOnFile) {
    }

    /**
     * counterpartName is the other person's name as shown on the trip, and
     * nothing else about them. myRating is what this person gave.
     */
    public record Trip(UUID bookingId, String myRole, String type, String category, String status,
                       String pickup, String drop, BigDecimal fareEstimate, BigDecimal finalFare,
                       Instant requestedAt, Instant completedAt, Instant cancelledAt,
                       String counterpartName, Payment payment, RatingGiven myRating) {
    }

    /** driverPayout only for the partner's own export - it is her earning, not the rider's business. */
    public record Payment(BigDecimal amount, String method, String status, BigDecimal driverPayout, Instant capturedAt) {
    }

    public record RatingGiven(Integer stars, String comment, Instant submittedAt) {
    }

    public record RatingsReceived(Double averageStarsReceived, int totalRatingsReceived) {
    }

    /** The person's tickets and the thread as they see it - internal operator notes are never included. */
    public record SupportTicket(UUID ticketId, String category, String subject, String description, String status,
                                Instant createdAt, Instant resolvedAt, List<TicketMessage> messages) {
    }

    public record TicketMessage(String from, String message, Instant createdAt) {
    }
}
