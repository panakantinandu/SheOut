package com.sheout.booking.internal;

import com.sheout.booking.BookingError;
import com.sheout.booking.BookingStatus;
import com.sheout.booking.BookingType;
import com.sheout.booking.DropOffDeviationReason;
import com.sheout.booking.ParcelDeliveryApi;
import com.sheout.booking.ParcelDeliveryCodeIssued;
import com.sheout.booking.ParcelDeliveryExceptionRaised;
import com.sheout.booking.ParcelDetails;
import com.sheout.booking.ParcelDisputeClosed;
import com.sheout.booking.ParcelDisputeOpened;
import com.sheout.booking.ParcelDisputeOutcome;
import com.sheout.booking.ParcelEvidence;
import com.sheout.booking.ParcelExceptionReason;
import com.sheout.booking.ParcelPhotoKind;
import com.sheout.booking.ParcelSenderChoice;
import com.sheout.dispatch.DriverLocation;
import com.sheout.dispatch.DriverLocationApi;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.geo.GeoDistance;
import com.sheout.sharedkernel.storage.DocumentRules;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Proof of delivery for parcels. A photo is evidence, not prevention: what
 * prevents a false "Delivered" is the receiver's code, which the partner
 * cannot know, and the partner's earnings being held until the delivery has
 * stood unchallenged for a day (payouts). This class owns the code, the
 * photos, the drop check, the receiver-absent flow and the dispute record.
 * <p>
 * BookingService calls in at three moments it already owns - accept, start
 * and complete - inside its own locked transaction, so a delivery's state and
 * its proof change together. A delivery with no row here was booked before
 * proof of delivery existed and finishes under the old rules.
 */
@Service
public class ParcelDeliveryService implements ParcelDeliveryApi {

    private static final Logger log = LoggerFactory.getLogger(ParcelDeliveryService.class);

    /** A 10-digit Indian mobile number, however it was typed: +91, 0, spaces and dashes allowed. */
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^(?:\\+?91|0)?([6-9]\\d{9})$");
    /** "Receiver not there" is believed with a photo taken just now, not one from earlier in the trip. */
    private static final Duration EXCEPTION_PHOTO_FRESH_FOR = Duration.ofMinutes(15);
    /** Her position is stamped on a photo only when it is this recent. */
    private static final Duration PHOTO_LOCATION_FRESH_FOR = Duration.ofSeconds(60);

    private final ParcelDeliveryRepository parcels;
    private final ParcelPhotoRepository photos;
    private final BookingRepository bookings;
    private final DocumentStorage storage;
    private final DriverLocationApi locations;
    private final DomainEventPublisher events;
    private final ParcelRules rules;
    private final DropoffGeofence dropGeofence;
    private BookingEventLog eventLog = BookingEventLog.NONE;

    public ParcelDeliveryService(ParcelDeliveryRepository parcels, ParcelPhotoRepository photos, BookingRepository bookings,
                                 DocumentStorage storage, DriverLocationApi locations, DomainEventPublisher events,
                                 ParcelRules rules,
                                 @Value("${sheout.booking.completion.driver-location-max-age-seconds:30}") long locationMaxAgeSeconds) {
        this.parcels = parcels;
        this.photos = photos;
        this.bookings = bookings;
        this.storage = storage;
        this.locations = locations;
        this.events = events;
        this.rules = rules;
        this.dropGeofence = new DropoffGeofence(rules.deliveryRadiusMetres(), Duration.ofSeconds(locationMaxAgeSeconds));
    }

    @Autowired
    void setEventLog(BookingEventLog eventLog) {
        this.eventLog = eventLog;
    }

    public ParcelRules rules() {
        return rules;
    }

    // ---- at booking ---------------------------------------------------------

    /** The receiver, the limits and the sender's agreement - checked before the booking is created. */
    Optional<BookingError> validate(ParcelDetails details) {
        if (details == null || clean(details.receiverName(), 80) == null || details.receiverPhone() == null
                || !details.prohibitedItemsAcknowledged() || !details.sizeAcknowledged()) {
            return Optional.of(BookingError.PARCEL_DETAILS_REQUIRED);
        }
        if (BookingService.PHONE_LIKE.matcher(details.receiverName()).find()) {
            return Optional.of(BookingError.PARCEL_DETAILS_REQUIRED);
        }
        if (normalisePhone(details.receiverPhone()) == null) {
            return Optional.of(BookingError.PARCEL_RECEIVER_PHONE_INVALID);
        }
        if (!rules.prohibitedItemsVersion().equals(details.prohibitedItemsVersion())) {
            return Optional.of(BookingError.PARCEL_RULES_CHANGED);
        }
        BigDecimal value = details.declaredValue();
        if (value != null && (value.signum() < 0 || value.compareTo(rules.maxDeclaredValue()) > 0)) {
            return Optional.of(BookingError.PARCEL_DECLARED_VALUE_TOO_HIGH);
        }
        return Optional.empty();
    }

    /** Called once the booking exists; validate() has already passed. */
    void recordDetails(UUID bookingId, ParcelDetails details, Instant now) {
        BigDecimal value = details.declaredValue() == null ? null
                : details.declaredValue().setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
        parcels.save(new ParcelDeliveryEntity(bookingId, clean(details.receiverName(), 80),
                normalisePhone(details.receiverPhone()), value, details.prohibitedItemsVersion(),
                details.sizeAcknowledged(), now));
    }

    static String normalisePhone(String raw) {
        if (raw == null) {
            return null;
        }
        var m = INDIAN_MOBILE.matcher(raw.replaceAll("[\\s-]", ""));
        return m.matches() ? "+91" + m.group(1) : null;
    }

    private static String clean(String text, int max) {
        if (text == null) {
            return null;
        }
        String c = text.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (c.isEmpty()) {
            return null;
        }
        return c.length() > max ? c.substring(0, max) : c;
    }

    // ---- accept: the delivery code ------------------------------------------

    /**
     * The receiver's code, made when the partner commits to the trip so the
     * sender can pass it on before the parcel is collected. Never the same
     * as the pickup code, or the partner would hold the answer to both.
     */
    void issueCode(BookingEntity booking) {
        if (booking.getType() != BookingType.DELIVERY) {
            return;
        }
        parcels.findById(booking.getId()).ifPresent(parcel -> {
            if (parcel.getDeliveryCode() != null) {
                return;
            }
            String code;
            do {
                code = PickupCode.generate();
            } while (code.equals(booking.getPickupOtp()));
            parcel.issueCode(code);
            parcels.save(parcel);
            eventLog.record(booking.getId(), "DELIVERY_CODE_ISSUED", null, null, null);
            events.publish(new ParcelDeliveryCodeIssued(booking.getId(), parcel.getReceiverPhone(), code));
        });
    }

    // ---- start: the pickup photo --------------------------------------------

    /** The parcel is photographed as it is handed over, before the code moves the trip on. */
    Optional<BookingError> refuseStart(BookingEntity booking) {
        if (booking.getType() != BookingType.DELIVERY || !rules.photosRequired() || parcels.findById(booking.getId()).isEmpty()) {
            return Optional.empty();
        }
        return photos.existsByBookingIdAndKindAndDeletedAtIsNull(booking.getId(), ParcelPhotoKind.PICKUP)
                ? Optional.empty()
                : Optional.of(BookingError.PARCEL_PICKUP_PHOTO_REQUIRED);
    }

    // ---- complete: code, photo, drop check ----------------------------------

    /** The proof record for a delivery being completed, when it has one. Called under the booking's lock. */
    Optional<ParcelDeliveryEntity> proofFor(BookingEntity booking) {
        return booking.getType() == BookingType.DELIVERY ? parcels.findById(booking.getId()) : Optional.empty();
    }

    /** The parcel's own drop radius (PARCEL_DELIVERY_RADIUS_M), not the ride one. */
    DropoffGeofence.Decision checkAtDrop(DriverLocation location, BookingEntity booking, Instant now) {
        return dropGeofence.check(location, booking.getDrop().getLat(), booking.getDrop().getLng(), now);
    }

    /**
     * Everything a delivery needs before it may be marked delivered, in an
     * order that never burns a code attempt on a refusal that was not about
     * the code: the handover photo first, the code last. A sender who chose
     * "leave it with someone" has answered in place of a code.
     * <p>
     * Away from the drop the reason has already been required by
     * completeTrip; here it is recorded and the delivery flagged for an
     * operator. Never silent.
     */
    Optional<BookingError> checkAndRecordDelivery(BookingEntity booking, ParcelDeliveryEntity parcel, String submittedCode,
                                                  DriverLocation location, DropoffGeofence.Decision decision,
                                                  DropOffDeviationReason reason, String note, Instant now) {
        boolean leftWithSomeone = parcel.getSenderChoice() == ParcelSenderChoice.LEAVE_WITH;
        if (rules.photosRequired() && !photos.existsByBookingIdAndKindAndDeletedAtIsNull(booking.getId(), ParcelPhotoKind.DELIVERY)) {
            return Optional.of(BookingError.PARCEL_DELIVERY_PHOTO_REQUIRED);
        }
        if (!leftWithSomeone) {
            if (parcel.codeLocked()) {
                return Optional.of(BookingError.PARCEL_DELIVERY_CODE_LOCKED);
            }
            if (!PickupCode.isWellFormed(submittedCode)) {
                return Optional.of(BookingError.PARCEL_DELIVERY_CODE_REQUIRED);
            }
            if (!PickupCode.matches(parcel.getDeliveryCode(), submittedCode)) {
                parcel.recordFailedCodeAttempt();
                parcels.save(parcel);
                eventLog.record(booking.getId(), "DELIVERY_CODE_WRONG", null, null,
                        "attempt " + parcel.getDeliveryCodeAttempts() + " of " + PickupCode.MAX_ATTEMPTS);
                return Optional.of(parcel.codeLocked()
                        ? BookingError.PARCEL_DELIVERY_CODE_LOCKED : BookingError.INVALID_PARCEL_DELIVERY_CODE);
            }
        }
        ParcelDeliveryEntity.DropCheck check = switch (decision) {
            case AT_DROP_OFF -> ParcelDeliveryEntity.DropCheck.AT_DROP;
            case OUTSIDE_DROP_OFF -> ParcelDeliveryEntity.DropCheck.OUTSIDE;
            case LOCATION_UNAVAILABLE -> ParcelDeliveryEntity.DropCheck.UNAVAILABLE;
        };
        Integer distance = decision == DropoffGeofence.Decision.LOCATION_UNAVAILABLE || location == null ? null
                : (int) Math.round(GeoDistance.haversineKm(location.lat(), location.lng(),
                        booking.getDrop().getLat(), booking.getDrop().getLng()) * 1000);
        parcel.recordDelivered(now, !leftWithSomeone, check, distance, reason, note);
        if (check == ParcelDeliveryEntity.DropCheck.OUTSIDE) {
            parcel.flagForReview("OUTSIDE_DROP", now);
        } else if (check == ParcelDeliveryEntity.DropCheck.UNAVAILABLE) {
            parcel.flagForReview("NO_LOCATION", now);
        }
        parcels.save(parcel);
        eventLog.record(booking.getId(), leftWithSomeone ? "DELIVERED_LEFT_WITH" : "DELIVERED_BY_CODE", null, null,
                check + (distance == null ? "" : " " + distance + " m") + (reason == null || check == ParcelDeliveryEntity.DropCheck.AT_DROP ? "" : " " + reason));
        return Optional.empty();
    }

    // ---- photos ---------------------------------------------------------------

    /**
     * A photo from the partner's camera. The server's own clock and her last
     * trusted position are what is recorded, never anything the phone claims.
     * The same picture used on another of her deliveries is kept and flagged.
     */
    @Transactional
    public Result<Void, BookingError> uploadPhoto(UUID bookingId, UUID driverId, ParcelPhotoKind kind, byte[] content) {
        Optional<BookingEntity> found = bookings.findById(bookingId);
        if (found.isEmpty() || !driverId.equals(found.get().getDriverId())) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();
        Optional<ParcelDeliveryEntity> parcel = proofFor(booking);
        if (parcel.isEmpty()) {
            return Result.failure(BookingError.NOT_A_PARCEL);
        }
        BookingStatus needed = kind == ParcelPhotoKind.PICKUP ? BookingStatus.ACCEPTED : BookingStatus.IN_PROGRESS;
        if (booking.getStatus() != needed) {
            return Result.failure(BookingError.INVALID_STATE_TRANSITION);
        }
        if (content == null || content.length == 0 || content.length > rules.maxPhotoBytes()) {
            return Result.failure(BookingError.PARCEL_PHOTO_INVALID);
        }
        String type = DocumentRules.photoTypeOf(content);
        if (type == null || DocumentRules.check(new DocumentUpload("parcel", type, content)) != null) {
            return Result.failure(BookingError.PARCEL_PHOTO_INVALID);
        }
        Instant now = Instant.now();
        DriverLocation at = locations.findLocation(driverId)
                .filter(l -> l.recordedAt().isAfter(now.minus(PHOTO_LOCATION_FRESH_FOR)))
                .orElse(null);
        String hash = sha256(content);
        String extension = type.equals("image/png") ? ".png" : type.equals("image/webp") ? ".webp" : ".jpg";
        String key = storage.store(driverId, "parcel-" + kind.name().toLowerCase(),
                new DocumentUpload(kind.name().toLowerCase() + extension, type, content));
        photos.save(new ParcelPhotoEntity(bookingId, kind, key, driverId, now,
                at == null ? null : at.lat(), at == null ? null : at.lng(), hash));
        if (photos.existsByUploadedByAndContentHashAndBookingIdNot(driverId, hash, bookingId)) {
            ParcelDeliveryEntity p = parcel.get();
            p.flagForReview("REUSED_PHOTO", now);
            parcels.save(p);
        }
        eventLog.record(bookingId, "PARCEL_PHOTO_" + kind.name(), null, null, at == null ? "no recent location" : null);
        return Result.success(null);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- receiver not there -------------------------------------------------

    /**
     * The partner cannot hand it over. Believed only from the drop itself,
     * with a photo taken there just now; free text alone is never enough.
     * The sender is asked to choose, and the partner waits for her answer.
     */
    @Transactional
    public Result<Void, BookingError> raiseException(UUID bookingId, UUID driverId, ParcelExceptionReason reason, String note) {
        Optional<BookingEntity> found = bookings.findLockedById(bookingId);
        if (found.isEmpty() || !driverId.equals(found.get().getDriverId())) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();
        Optional<ParcelDeliveryEntity> parcel = proofFor(booking);
        if (parcel.isEmpty()) {
            return Result.failure(BookingError.NOT_A_PARCEL);
        }
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            return Result.failure(BookingError.INVALID_STATE_TRANSITION);
        }
        String trimmed = clean(note, 500);
        if (reason == null) {
            return Result.failure(BookingError.PARCEL_EXCEPTION_NOTE_REQUIRED);
        }
        if (reason.requiresNote() && trimmed == null) {
            return Result.failure(BookingError.PARCEL_EXCEPTION_NOTE_REQUIRED);
        }
        Instant now = Instant.now();
        DropoffGeofence.Decision where = checkAtDrop(locations.findLocation(driverId).orElse(null), booking, now);
        if (where == DropoffGeofence.Decision.LOCATION_UNAVAILABLE) {
            return Result.failure(BookingError.DRIVER_LOCATION_UNAVAILABLE);
        }
        if (where == DropoffGeofence.Decision.OUTSIDE_DROP_OFF) {
            return Result.failure(BookingError.DRIVER_NOT_AT_DROP_OFF);
        }
        if (rules.photosRequired() && photos.findFirstByBookingIdAndKindOrderByTakenAtDesc(bookingId, ParcelPhotoKind.EXCEPTION)
                .filter(p -> p.getDeletedAt() == null && p.getTakenAt().isAfter(now.minus(EXCEPTION_PHOTO_FRESH_FOR)))
                .isEmpty()) {
            return Result.failure(BookingError.PARCEL_EXCEPTION_PHOTO_REQUIRED);
        }
        ParcelDeliveryEntity p = parcel.get();
        p.raiseException(reason, trimmed, now);
        parcels.save(p);
        eventLog.record(bookingId, "PARCEL_EXCEPTION", null, null, reason + (trimmed == null ? "" : ": " + trimmed));
        events.publish(new ParcelDeliveryExceptionRaised(bookingId, booking.getCustomerId(), driverId));
        return Result.success(null);
    }

    /**
     * The sender decides. Wait: the partner keeps waiting. Leave with: the
     * partner hands it to the person named (photographed, no code). Return:
     * the drop becomes the pickup, and the one-way fare is added again - it
     * ends on the same delivery code, now in the sender's own hands.
     */
    @Transactional
    public Result<Void, BookingError> recordSenderChoice(UUID bookingId, UUID customerId, ParcelSenderChoice choice, String leaveWithName) {
        Optional<BookingEntity> found = bookings.findLockedById(bookingId);
        if (found.isEmpty() || !customerId.equals(found.get().getCustomerId())) {
            return Result.failure(BookingError.BOOKING_NOT_FOUND);
        }
        BookingEntity booking = found.get();
        Optional<ParcelDeliveryEntity> parcel = proofFor(booking);
        if (parcel.isEmpty()) {
            return Result.failure(BookingError.NOT_A_PARCEL);
        }
        ParcelDeliveryEntity p = parcel.get();
        if (booking.getStatus() != BookingStatus.IN_PROGRESS || !p.awaitingSenderChoice() || choice == null) {
            return Result.failure(BookingError.PARCEL_NO_EXCEPTION_PENDING);
        }
        String name = clean(leaveWithName, 80);
        if (choice == ParcelSenderChoice.LEAVE_WITH && (name == null || BookingService.PHONE_LIKE.matcher(name).find())) {
            return Result.failure(BookingError.PARCEL_LEAVE_WITH_NAME_REQUIRED);
        }
        Instant now = Instant.now();
        BigDecimal returnFare = null;
        if (choice == ParcelSenderChoice.RETURN) {
            returnFare = booking.getFareEstimate();
            BigDecimal distance = booking.getQuotedDistanceKm() == null ? null : booking.getQuotedDistanceKm().multiply(BigDecimal.valueOf(2));
            booking.changeDestination(GeoAddressEmbeddable.from(booking.getPickup().toGeoAddress()),
                    booking.getFareEstimate().add(returnFare), distance,
                    Boolean.TRUE.equals(booking.getQuotedDistanceRouted()), now);
            bookings.save(booking);
        }
        p.recordSenderChoice(choice, name, returnFare, now);
        parcels.save(p);
        eventLog.record(bookingId, "PARCEL_SENDER_CHOICE", null, null,
                choice + (choice == ParcelSenderChoice.RETURN ? " (return fare " + returnFare + ")" : ""));
        return Result.success(null);
    }

    // ---- what the sender and her partner see --------------------------------

    /**
     * The parcel as one participant may see it. The sender: everything she
     * entered, and the delivery code to pass on. Her partner: the receiver's
     * first name only - no number, no value, never the code.
     */
    @Transactional(readOnly = true)
    public Optional<ParticipantView> viewFor(UUID bookingId, UUID callerId) {
        Optional<BookingEntity> found = bookings.findById(bookingId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        BookingEntity booking = found.get();
        boolean sender = callerId.equals(booking.getCustomerId());
        boolean partner = booking.getDriverId() != null && callerId.equals(booking.getDriverId());
        if (!sender && !partner) {
            return Optional.empty();
        }
        return proofFor(booking).map(p -> {
            List<ParcelPhotoEntity> all = photos.findByBookingIdOrderByTakenAtAsc(bookingId);
            boolean codeVisible = sender && p.getDeliveryCode() != null
                    && (booking.getStatus() == BookingStatus.ACCEPTED || booking.getStatus() == BookingStatus.IN_PROGRESS);
            return new ParticipantView(
                    sender ? "SENDER" : "PARTNER",
                    sender ? p.getReceiverName() : firstName(p.getReceiverName()),
                    sender ? p.getReceiverPhone() : null,
                    sender ? p.getDeclaredValue() : null,
                    codeVisible ? p.getDeliveryCode() : null,
                    p.getDeliveryVerifiedAt() != null,
                    Math.max(0, PickupCode.MAX_ATTEMPTS - p.getDeliveryCodeAttempts()),
                    all.stream().anyMatch(ph -> ph.getKind() == ParcelPhotoKind.PICKUP),
                    all.stream().anyMatch(ph -> ph.getKind() == ParcelPhotoKind.DELIVERY),
                    p.awaitingSenderChoice(),
                    p.getExceptionReason(),
                    p.getExceptionNote(),
                    p.getExceptionAt(),
                    p.getSenderChoice(),
                    p.getLeaveWithName(),
                    p.getReturnFare() != null && p.getReturnFareWaivedAt() == null ? p.getReturnFare() : null,
                    booking.getFareEstimate(),
                    rules.photosRequired(),
                    (int) Math.round(rules.deliveryRadiusMetres()),
                    all.stream().map(this::photoView).toList());
        });
    }

    /** What GET /bookings/{id}/parcel returns - see viewFor for who sees which field. */
    public record ParticipantView(
            String viewer,
            String receiverName,
            String receiverPhone,
            BigDecimal declaredValue,
            String deliveryCode,
            boolean delivered,
            int codeAttemptsLeft,
            boolean pickupPhotoTaken,
            boolean deliveryPhotoTaken,
            boolean awaitingSenderChoice,
            ParcelExceptionReason exceptionReason,
            String exceptionNote,
            Instant exceptionAt,
            ParcelSenderChoice senderChoice,
            String leaveWithName,
            BigDecimal returnFare,
            /** One-way fare now, so the sender sees what "bring it back" would add before she chooses. */
            BigDecimal currentFare,
            boolean photosRequired,
            int deliveryRadiusMetres,
            List<ParcelEvidence.Photo> photos
    ) {
    }

    private ParcelEvidence.Photo photoView(ParcelPhotoEntity p) {
        boolean deleted = p.getDeletedAt() != null || p.getStorageKey() == null;
        return new ParcelEvidence.Photo(p.getKind(), p.getTakenAt(), p.getLat(), p.getLng(),
                deleted ? null : storage.resolveUrl(p.getStorageKey()), deleted);
    }

    static String firstName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return null;
        }
        return phone.substring(0, Math.min(3, phone.length())) + "******" + phone.substring(phone.length() - 4);
    }

    // ---- operators (ParcelDeliveryApi) --------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<ParcelEvidence> evidence(UUID bookingId) {
        return parcels.findById(bookingId).map(this::toEvidence);
    }

    private ParcelEvidence toEvidence(ParcelDeliveryEntity p) {
        return new ParcelEvidence(p.getBookingId(), p.getReceiverName(), mask(p.getReceiverPhone()), p.getDeclaredValue(),
                p.getProhibitedAckVersion(), p.getAcknowledgedAt(), p.getDeliveryCode() != null,
                p.getDeliveryCodeAttempts(), p.codeLocked(), p.getDeliveryVerifiedAt(),
                p.getDropCheck() == null ? null : p.getDropCheck().name(), p.getDropCheckDistanceM(),
                p.getOutsideReason(), p.getOutsideNote(), p.getReviewFlaggedAt(), p.getReviewFlagReason(),
                p.getReviewedAt(), p.getReviewNote(), p.getExceptionReason(), p.getExceptionNote(), p.getExceptionAt(),
                p.getSenderChoice(), p.getSenderChoiceAt(), p.getLeaveWithName(), p.getReturnFare(),
                p.getReturnFareWaivedAt(), p.getDisputeOpenedAt(), p.getDisputeNote(), p.getDisputeClosedAt(),
                p.getDisputeOutcome(), p.getDisputeResolutionNote(),
                photos.findByBookingIdOrderByTakenAtAsc(p.getBookingId()).stream().map(this::photoView).toList());
    }

    @Override
    @Transactional
    public Result<ParcelEvidence, BookingError> openDispute(UUID bookingId, UUID adminAccountId, String note) {
        Optional<BookingEntity> booking = bookings.findLockedById(bookingId);
        Optional<ParcelDeliveryEntity> parcel = booking.flatMap(this::proofFor);
        if (parcel.isEmpty()) {
            return Result.failure(BookingError.NOT_A_PARCEL);
        }
        ParcelDeliveryEntity p = parcel.get();
        if (p.disputeOpen()) {
            return Result.failure(BookingError.PARCEL_DISPUTE_ALREADY_OPEN);
        }
        p.openDispute(adminAccountId, clean(note, 1000), Instant.now());
        parcels.save(p);
        eventLog.record(bookingId, "PARCEL_DISPUTE_OPENED", null, null, p.getDisputeNote());
        events.publish(new ParcelDisputeOpened(bookingId, booking.get().getCustomerId(), booking.get().getDriverId()));
        return Result.success(toEvidence(p));
    }

    @Override
    @Transactional
    public Result<ParcelEvidence, BookingError> closeDispute(UUID bookingId, UUID adminAccountId, ParcelDisputeOutcome outcome, String note) {
        Optional<BookingEntity> booking = bookings.findLockedById(bookingId);
        Optional<ParcelDeliveryEntity> parcel = booking.flatMap(this::proofFor);
        if (parcel.isEmpty()) {
            return Result.failure(BookingError.NOT_A_PARCEL);
        }
        ParcelDeliveryEntity p = parcel.get();
        if (!p.disputeOpen()) {
            return Result.failure(BookingError.PARCEL_NO_DISPUTE_OPEN);
        }
        p.closeDispute(adminAccountId, outcome, clean(note, 1000), Instant.now());
        parcels.save(p);
        eventLog.record(bookingId, "PARCEL_DISPUTE_CLOSED", null, null, outcome + ": " + p.getDisputeResolutionNote());
        events.publish(new ParcelDisputeClosed(bookingId, booking.get().getDriverId(), outcome, adminAccountId, p.getDisputeResolutionNote()));
        return Result.success(toEvidence(p));
    }

    @Override
    @Transactional
    public Result<ParcelEvidence, BookingError> markReviewed(UUID bookingId, UUID adminAccountId, String note) {
        Optional<ParcelDeliveryEntity> parcel = parcels.findById(bookingId);
        if (parcel.isEmpty()) {
            return Result.failure(BookingError.NOT_A_PARCEL);
        }
        ParcelDeliveryEntity p = parcel.get();
        if (p.getReviewFlaggedAt() == null) {
            return Result.failure(BookingError.INVALID_STATE_TRANSITION);
        }
        if (p.getReviewedAt() == null) {
            p.recordReview(adminAccountId, clean(note, 1000), Instant.now());
            parcels.save(p);
            eventLog.record(bookingId, "PARCEL_REVIEWED", null, null, p.getReviewNote());
        }
        return Result.success(toEvidence(p));
    }

    @Override
    @Transactional
    public Result<ParcelEvidence, BookingError> waiveReturnFare(UUID bookingId, UUID adminAccountId, String note) {
        Optional<BookingEntity> found = bookings.findLockedById(bookingId);
        Optional<ParcelDeliveryEntity> parcel = found.flatMap(this::proofFor);
        if (parcel.isEmpty()) {
            return Result.failure(BookingError.NOT_A_PARCEL);
        }
        BookingEntity booking = found.get();
        ParcelDeliveryEntity p = parcel.get();
        if (p.getReturnFare() == null || p.getReturnFareWaivedAt() != null) {
            return Result.failure(BookingError.INVALID_STATE_TRANSITION);
        }
        if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
            return Result.failure(booking.getStatus() == BookingStatus.COMPLETED
                    ? BookingError.PARCEL_RETURN_FARE_ALREADY_CHARGED : BookingError.INVALID_STATE_TRANSITION);
        }
        booking.reduceFareEstimate(p.getReturnFare());
        bookings.save(booking);
        p.waiveReturnFare(adminAccountId, Instant.now());
        parcels.save(p);
        eventLog.record(bookingId, "PARCEL_RETURN_FARE_WAIVED", null, null, clean(note, 500));
        return Result.success(toEvidence(p));
    }

    // ---- retention ----------------------------------------------------------

    /**
     * Photos and receivers' numbers go after the retention period; a delivery
     * under dispute keeps both until the dispute is closed. The photo rows
     * stay, with the file gone, as the record that a photo was taken.
     *
     * @return how many photos were deleted
     */
    @Transactional
    public int sweepRetention(Instant now) {
        Instant cutoff = now.minus(rules.photoRetention());
        int deleted = 0;
        for (ParcelPhotoEntity photo : photos.findTop500ByTakenAtBeforeAndDeletedAtIsNull(cutoff)) {
            if (parcels.findById(photo.getBookingId()).map(ParcelDeliveryEntity::disputeOpen).orElse(false)) {
                continue;
            }
            try {
                if (photo.getStorageKey() != null) {
                    storage.delete(photo.getStorageKey());
                }
            } catch (RuntimeException e) {
                log.warn("Could not delete parcel photo {}: {}", photo.getId(), e.getClass().getSimpleName());
                continue;
            }
            photo.markDeleted(now);
            photos.save(photo);
            deleted++;
        }
        for (ParcelDeliveryEntity parcel : parcels.findTop500ByCreatedAtBeforeAndReceiverPhoneIsNotNull(cutoff)) {
            if (!parcel.disputeOpen()) {
                parcel.eraseReceiverPhone(now);
                parcels.save(parcel);
            }
        }
        return deleted;
    }
}
