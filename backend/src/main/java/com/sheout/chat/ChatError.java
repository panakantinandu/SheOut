package com.sheout.chat;

/**
 * Expected outcomes of sending or reading a thread, returned via Result
 * rather than thrown - a closed thread is a normal state, not a fault.
 */
public enum ChatError {

    /**
     * No such booking, OR the caller is not on it. Deliberately one value
     * for both: the controller answers 404 either way, so a caller cannot
     * tell a booking that does not exist from one that is not theirs. Same
     * reasoning BookingController.requireParticipant records.
     */
    BOOKING_NOT_FOUND,

    /**
     * The trip is not live. Chat opens at ACCEPTED and closes the moment the
     * booking completes or is cancelled - see ChatService.
     */
    CHAT_CLOSED,

    /** Empty, whitespace-only, or past the length the column allows. */
    INVALID_MESSAGE,

    /**
     * The message looks like it carries a phone number. Refused rather than
     * redacted - see ChatService.looksLikeAPhoneNumber.
     */
    CONTACT_DETAILS_NOT_ALLOWED
}
