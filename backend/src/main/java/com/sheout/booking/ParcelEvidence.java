package com.sheout.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything an operator needs to settle a parcel complaint, on one screen:
 * who it was for, what the sender agreed to, the delivery code's history,
 * both photos (short signed links), the drop check, the receiver-absent
 * steps and the dispute. The route and times are on the booking itself.
 * The receiver's phone is shown masked; nobody needs it whole to decide.
 */
public record ParcelEvidence(
        UUID bookingId,
        String receiverName,
        String receiverPhoneMasked,
        BigDecimal declaredValue,
        String prohibitedItemsVersion,
        Instant acknowledgedAt,
        boolean deliveryCodeIssued,
        int deliveryCodeAttempts,
        boolean deliveryCodeLocked,
        Instant deliveryVerifiedAt,
        String dropCheck,
        Integer dropCheckDistanceM,
        DropOffDeviationReason outsideReason,
        String outsideNote,
        Instant reviewFlaggedAt,
        String reviewFlagReason,
        Instant reviewedAt,
        String reviewNote,
        ParcelExceptionReason exceptionReason,
        String exceptionNote,
        Instant exceptionAt,
        ParcelSenderChoice senderChoice,
        Instant senderChoiceAt,
        String leaveWithName,
        BigDecimal returnFare,
        Instant returnFareWaivedAt,
        Instant disputeOpenedAt,
        String disputeNote,
        Instant disputeClosedAt,
        ParcelDisputeOutcome disputeOutcome,
        String disputeResolutionNote,
        List<Photo> photos
) {

    /** One photo: when and where the server says it was taken; url null once retention has deleted it. */
    public record Photo(ParcelPhotoKind kind, Instant takenAt, Double lat, Double lng, String url, boolean deleted) {
    }
}
