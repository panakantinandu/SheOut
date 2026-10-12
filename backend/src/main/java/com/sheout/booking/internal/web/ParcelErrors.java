package com.sheout.booking.internal.web;

import com.sheout.booking.BookingError;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The HTTP answer for each parcel refusal. Codes, not reason phrases: each
 * one leads the app somewhere different (open the camera, keep the keypad
 * open, show the new prohibited-items list). The message is the English
 * fallback; the apps say it in her language from the code.
 */
final class ParcelErrors {

    private ParcelErrors() {
    }

    static ApiException toApiException(BookingError error) {
        return switch (error) {
            case PARCEL_DETAILS_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "PARCEL_DETAILS_REQUIRED",
                    "Add the receiver's name and number, and confirm the parcel rules.");
            case PARCEL_RECEIVER_PHONE_INVALID -> new ApiException(HttpStatus.BAD_REQUEST, "PARCEL_RECEIVER_PHONE_INVALID",
                    "Enter the receiver's 10-digit mobile number.");
            case PARCEL_RULES_CHANGED -> new ApiException(HttpStatus.CONFLICT, "PARCEL_RULES_CHANGED",
                    "The parcel rules have changed. Read them again and confirm.");
            case PARCEL_DECLARED_VALUE_TOO_HIGH -> new ApiException(HttpStatus.BAD_REQUEST, "PARCEL_DECLARED_VALUE_TOO_HIGH",
                    "The declared value is above the limit for a parcel.");
            case PARCEL_PICKUP_PHOTO_REQUIRED -> new ApiException(HttpStatus.CONFLICT, "PARCEL_PICKUP_PHOTO_REQUIRED",
                    "Take a photo of the parcel as it is handed to you, then enter the code.");
            case PARCEL_DELIVERY_PHOTO_REQUIRED -> new ApiException(HttpStatus.CONFLICT, "PARCEL_DELIVERY_PHOTO_REQUIRED",
                    "Take a photo of the parcel at the handover, then mark it delivered.");
            case PARCEL_DELIVERY_CODE_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "PARCEL_DELIVERY_CODE_REQUIRED",
                    "Enter the four-digit delivery code the receiver gives you.");
            case INVALID_PARCEL_DELIVERY_CODE -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARCEL_DELIVERY_CODE",
                    "That code doesn't match. Ask the receiver to read it out again.");
            case PARCEL_DELIVERY_CODE_LOCKED -> new ApiException(HttpStatus.CONFLICT, "PARCEL_DELIVERY_CODE_LOCKED",
                    "Too many wrong codes. Tap \"Can't hand it over\" so the sender can decide.");
            case PARCEL_EXCEPTION_PHOTO_REQUIRED -> new ApiException(HttpStatus.CONFLICT, "PARCEL_EXCEPTION_PHOTO_REQUIRED",
                    "Take a photo of the door or the place first.");
            case PARCEL_EXCEPTION_NOTE_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "PARCEL_EXCEPTION_NOTE_REQUIRED",
                    "Please say a little more about what happened.");
            case PARCEL_NO_EXCEPTION_PENDING -> new ApiException(HttpStatus.CONFLICT, "PARCEL_NO_EXCEPTION_PENDING",
                    "Your partner is not waiting for an answer now.");
            case PARCEL_LEAVE_WITH_NAME_REQUIRED -> new ApiException(HttpStatus.BAD_REQUEST, "PARCEL_LEAVE_WITH_NAME_REQUIRED",
                    "Name the person it can be left with.");
            case PARCEL_PHOTO_INVALID -> new ApiException(HttpStatus.BAD_REQUEST, "PARCEL_PHOTO_INVALID",
                    "That photo could not be used. Take it again.");
            case PARCEL_END_HERE_NOT_ALLOWED -> new ApiException(HttpStatus.CONFLICT, "PARCEL_END_HERE_NOT_ALLOWED",
                    "A delivery ends when the receiver gets it.");
            case PARCEL_RETURN_FARE_ALREADY_CHARGED -> new ApiException(HttpStatus.CONFLICT, "PARCEL_RETURN_FARE_ALREADY_CHARGED",
                    "The trip has ended, so the return fare is part of the charge. Refund it from Refunds instead.");
            case PARCEL_DISPUTE_ALREADY_OPEN -> new ApiException(HttpStatus.CONFLICT, "PARCEL_DISPUTE_ALREADY_OPEN",
                    "A dispute is already open for this delivery.");
            case PARCEL_NO_DISPUTE_OPEN -> new ApiException(HttpStatus.CONFLICT, "PARCEL_NO_DISPUTE_OPEN",
                    "There is no open dispute for this delivery.");
            case NOT_A_PARCEL, BOOKING_NOT_FOUND -> ApiException.notFound("No such booking");
            case DRIVER_NOT_AT_DROP_OFF -> new ApiException(HttpStatus.CONFLICT, "DRIVER_NOT_AT_DROP_OFF",
                    "Go to the drop point first.");
            case DRIVER_LOCATION_UNAVAILABLE -> new ApiException(HttpStatus.CONFLICT, "DRIVER_LOCATION_UNAVAILABLE",
                    "Your current location is unavailable or too old. Refresh your location and try again.");
            default -> new ApiException(HttpStatus.CONFLICT, "Conflict", "This action isn't valid for the booking's current status");
        };
    }
}
