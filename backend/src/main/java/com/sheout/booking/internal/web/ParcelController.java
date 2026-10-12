package com.sheout.booking.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingError;
import com.sheout.booking.ParcelExceptionReason;
import com.sheout.booking.ParcelPhotoKind;
import com.sheout.booking.ParcelSenderChoice;
import com.sheout.booking.internal.ParcelDeliveryService;
import com.sheout.booking.internal.ParcelRules;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * A parcel's proof of delivery, for the two people on the trip: the rules a
 * sender agrees to, the parcel as each of them may see it, the partner's
 * photos and "can't hand it over", and the sender's answer to that.
 * <p>
 * 404 for every refusal about a booking - not hers, no such booking, or not
 * a parcel - the enumeration-safe answer used throughout.
 */
@RestController
public class ParcelController {

    /** A handful per trip at most - pickup, delivery, a retake or two - with room to spare. */
    private static final int PHOTOS_PER_WINDOW = 30;
    private static final Duration PHOTO_WINDOW = Duration.ofMinutes(15);

    private final ParcelDeliveryService parcels;
    private final RateLimiter rateLimiter;

    public ParcelController(ParcelDeliveryService parcels, RateLimiter rateLimiter) {
        this.parcels = parcels;
        this.rateLimiter = rateLimiter;
    }

    /** What the sender is asked to agree to - the same list and version the server checks her booking against. */
    @GetMapping("/api/v1/parcels/rules")
    public ResponseEntity<RulesResponse> rules() {
        requireAuthenticated();
        ParcelRules r = parcels.rules();
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)))
                .body(new RulesResponse(r.maxWeightKg(), r.maxDeclaredValue(), r.prohibitedItemsVersion(),
                        ParcelRules.PROHIBITED_ITEMS, (int) Math.round(r.deliveryRadiusMetres()), r.photosRequired()));
    }

    public record RulesResponse(int maxWeightKg, BigDecimal maxDeclaredValue, String prohibitedItemsVersion,
                                List<String> prohibitedItems, int deliveryRadiusMetres, boolean photosRequired) {
    }

    @GetMapping("/api/v1/bookings/{bookingId}/parcel")
    public ResponseEntity<ParcelDeliveryService.ParticipantView> parcel(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireAuthenticated();
        return parcels.viewFor(bookingId, caller.accountId())
                .map(view -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view))
                .orElseThrow(() -> ApiException.notFound("No such booking"));
    }

    @PostMapping(value = "/api/v1/bookings/{bookingId}/parcel/photos", consumes = "multipart/form-data")
    public ResponseEntity<ParcelDeliveryService.ParticipantView> uploadPhoto(@PathVariable UUID bookingId,
                                                                             @RequestParam ParcelPhotoKind kind,
                                                                             @RequestParam("photo") MultipartFile photo) {
        CurrentAccount caller = requireRole(AccountRole.DRIVER);
        rateLimiter.tryConsume("parcel-photo:" + caller.accountId(), PHOTOS_PER_WINDOW, PHOTO_WINDOW)
                .orThrow("Too many photos in a short time. Please wait a few minutes.");
        byte[] bytes;
        try {
            bytes = photo.getBytes();
        } catch (IOException e) {
            throw ParcelErrors.toApiException(BookingError.PARCEL_PHOTO_INVALID);
        }
        respond(parcels.uploadPhoto(bookingId, caller.accountId(), kind, bytes));
        return view(bookingId, caller);
    }

    /** The partner cannot hand it over: why, from the drop, with a photo she has just taken there. */
    @PostMapping("/api/v1/bookings/{bookingId}/parcel/exception")
    public ResponseEntity<ParcelDeliveryService.ParticipantView> raiseException(@PathVariable UUID bookingId,
                                                                                @Valid @RequestBody ExceptionRequest request) {
        CurrentAccount caller = requireRole(AccountRole.DRIVER);
        respond(parcels.raiseException(bookingId, caller.accountId(), request.reason(), request.note()));
        return view(bookingId, caller);
    }

    public record ExceptionRequest(@NotNull ParcelExceptionReason reason, @Size(max = 500) String note) {
    }

    /** The sender's answer: wait, leave it with someone she names, or bring it back. */
    @PostMapping("/api/v1/bookings/{bookingId}/parcel/choice")
    public ResponseEntity<ParcelDeliveryService.ParticipantView> choose(@PathVariable UUID bookingId,
                                                                        @Valid @RequestBody ChoiceRequest request) {
        CurrentAccount caller = requireRole(AccountRole.CUSTOMER);
        respond(parcels.recordSenderChoice(bookingId, caller.accountId(), request.choice(), request.leaveWithName()));
        return view(bookingId, caller);
    }

    public record ChoiceRequest(@NotNull ParcelSenderChoice choice, @Size(max = 80) String leaveWithName) {
    }

    private ResponseEntity<ParcelDeliveryService.ParticipantView> view(UUID bookingId, CurrentAccount caller) {
        return parcels.viewFor(bookingId, caller.accountId())
                .map(v -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(v))
                .orElseThrow(() -> ApiException.notFound("No such booking"));
    }

    private static void respond(Result<Void, BookingError> result) {
        if (result.isFailure()) {
            throw ParcelErrors.toApiException(result.error());
        }
    }

    private static CurrentAccount requireAuthenticated() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    private static CurrentAccount requireRole(AccountRole role) {
        CurrentAccount caller = requireAuthenticated();
        if (caller.role() != role) {
            throw ApiException.forbidden(role + " role required");
        }
        return caller;
    }
}
