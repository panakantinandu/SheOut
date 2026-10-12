package com.sheout.admin.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingError;
import com.sheout.booking.ParcelDeliveryApi;
import com.sheout.booking.ParcelDisputeOutcome;
import com.sheout.booking.ParcelEvidence;
import com.sheout.payouts.EarningHold;
import com.sheout.payouts.EarningHoldApi;
import com.sheout.payouts.EarningHoldError;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresAnyPermission;
import com.sheout.staff.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The decisions on a parcel's evidence screen: open or close a dispute,
 * mark a flagged delivery reviewed, waive a return fare, and hold or release
 * the partner's earnings. The evidence itself comes with the trip's detail
 * (GET /admin/bookings/{id}). Every decision carries a note - it is the
 * record of what was decided and why - and is audited like any other write.
 */
@RestController
@RequestMapping("/api/v1/admin/bookings/{bookingId}")
public class AdminParcelController {

    private final ParcelDeliveryApi parcels;
    private final EarningHoldApi holds;

    public AdminParcelController(ParcelDeliveryApi parcels, EarningHoldApi holds) {
        this.parcels = parcels;
        this.holds = holds;
    }

    public record NoteRequest(@NotBlank @Size(max = 1000) String note) {
    }

    public record CloseRequest(@NotNull ParcelDisputeOutcome outcome, @NotBlank @Size(max = 1000) String note) {
    }

    @RequiresAnyPermission({Permission.SUPPORT_WORK, Permission.PAYOUTS_PREPARE})
    @PostMapping("/parcel/dispute")
    public ResponseEntity<ParcelEvidence> openDispute(@PathVariable UUID bookingId, @Valid @RequestBody NoteRequest request) {
        return parcel(parcels.openDispute(bookingId, caller().accountId(), request.note()));
    }

    /** Closing against the partner withholds her earnings, so it is a payouts decision. */
    @RequiresPermission(Permission.PAYOUTS_PREPARE)
    @PostMapping("/parcel/dispute/close")
    public ResponseEntity<ParcelEvidence> closeDispute(@PathVariable UUID bookingId, @Valid @RequestBody CloseRequest request) {
        return parcel(parcels.closeDispute(bookingId, caller().accountId(), request.outcome(), request.note()));
    }

    @RequiresPermission(Permission.SOS_RESPOND)
    @PostMapping("/parcel/reviewed")
    public ResponseEntity<ParcelEvidence> reviewed(@PathVariable UUID bookingId, @Valid @RequestBody NoteRequest request) {
        return parcel(parcels.markReviewed(bookingId, caller().accountId(), request.note()));
    }

    @RequiresPermission(Permission.REFUNDS_ISSUE)
    @PostMapping("/parcel/waive-return-fare")
    public ResponseEntity<ParcelEvidence> waiveReturnFare(@PathVariable UUID bookingId, @Valid @RequestBody NoteRequest request) {
        return parcel(parcels.waiveReturnFare(bookingId, caller().accountId(), request.note()));
    }

    @RequiresPermission(Permission.PAYOUTS_PREPARE)
    @PostMapping("/earnings/hold")
    public ResponseEntity<EarningHold> holdEarnings(@PathVariable UUID bookingId, @Valid @RequestBody NoteRequest request) {
        return hold(holds.hold(bookingId, caller().accountId(), request.note()));
    }

    @RequiresPermission(Permission.PAYOUTS_PREPARE)
    @PostMapping("/earnings/release")
    public ResponseEntity<EarningHold> releaseEarnings(@PathVariable UUID bookingId, @Valid @RequestBody NoteRequest request) {
        return hold(holds.release(bookingId, caller().accountId(), request.note()));
    }

    private static ResponseEntity<ParcelEvidence> parcel(Result<ParcelEvidence, BookingError> result) {
        if (result.isFailure()) {
            throw switch (result.error()) {
                case NOT_A_PARCEL, BOOKING_NOT_FOUND -> ApiException.notFound("No parcel delivery on this trip");
                case PARCEL_DISPUTE_ALREADY_OPEN -> new ApiException(HttpStatus.CONFLICT, "PARCEL_DISPUTE_ALREADY_OPEN", "A dispute is already open for this delivery.");
                case PARCEL_NO_DISPUTE_OPEN -> new ApiException(HttpStatus.CONFLICT, "PARCEL_NO_DISPUTE_OPEN", "There is no open dispute for this delivery.");
                case PARCEL_RETURN_FARE_ALREADY_CHARGED -> new ApiException(HttpStatus.CONFLICT, "PARCEL_RETURN_FARE_ALREADY_CHARGED",
                        "The trip has ended, so the return fare is part of the charge. Refund it from Refunds instead.");
                default -> new ApiException(HttpStatus.CONFLICT, "Conflict", "Nothing to do for this delivery right now.");
            };
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.value());
    }

    private static ResponseEntity<EarningHold> hold(Result<EarningHold, EarningHoldError> result) {
        if (result.isFailure()) {
            throw switch (result.error()) {
                case NOT_FOUND -> ApiException.notFound("No held parcel earnings on this trip");
                case ALREADY_SETTLED -> new ApiException(HttpStatus.CONFLICT, "ALREADY_SETTLED", "These earnings were already released or withheld.");
                case REASON_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Give a reason.");
            };
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.value());
    }

    private static CurrentAccount caller() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }
}
