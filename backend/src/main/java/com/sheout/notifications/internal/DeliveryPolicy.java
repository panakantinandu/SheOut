package com.sheout.notifications.internal;

/**
 * How one type of notification reaches somebody, beyond always being written
 * to her in-app inbox.
 * <p>
 * Push is first and free. Email and SMS are FALLBACKS, tried only when no push
 * copy was delivered - an SMS costs money per message and duplicates what the
 * phone already showed - with two exceptions: a receipt is email by nature,
 * and an offer or operator alert is push or nothing, because either one is
 * stale within a minute and a late text about it is just noise.
 *
 * @param push          send to every device the account has registered
 * @param emailFallback email when no push copy was delivered and an address is known
 * @param smsFallback   SMS when neither push nor email delivered
 * @param emailAlways   email regardless of push (receipts)
 */
record DeliveryPolicy(boolean push, boolean emailFallback, boolean smsFallback, boolean emailAlways) {

    static final DeliveryPolicy INBOX_ONLY = new DeliveryPolicy(false, false, false, false);
    static final DeliveryPolicy PUSH_ONLY = new DeliveryPolicy(true, false, false, false);
    static final DeliveryPolicy PUSH_THEN_SMS = new DeliveryPolicy(true, false, true, false);
    static final DeliveryPolicy PUSH_THEN_EMAIL_THEN_SMS = new DeliveryPolicy(true, true, true, false);
    static final DeliveryPolicy EMAIL = new DeliveryPolicy(false, false, false, true);
    /** Both, every time - for the few things somebody must not miss. */
    static final DeliveryPolicy PUSH_AND_EMAIL = new DeliveryPolicy(true, false, false, true);

    static DeliveryPolicy forType(NotificationType type) {
        return switch (type) {
            // She is looking at the searching screen the moment she books;
            // a push saying so would arrive on top of it.
            case BOOKING_REQUESTED -> INBOX_ONLY;
            case DRIVER_OFFER, SOS_OPERATOR_ALERT -> PUSH_ONLY;
            case BOOKING_ACCEPTED, DRIVER_ARRIVING, BOOKING_COMPLETED, BOOKING_CANCELLED,
                 NO_DRIVERS_AVAILABLE, PAYOUT_PAID -> PUSH_THEN_SMS;
            // The answer to "am I allowed to use this app yet", after a wait
            // on a person. Push and email both, not email only when push
            // failed: she may have said no to notifications, or have the app
            // closed for a day, and the whole point is that she does not have
            // to keep coming back to check. Rare enough that always emailing
            // costs nothing.
            case ACCOUNT_VERIFIED, ACCOUNT_VERIFICATION_REJECTED -> PUSH_AND_EMAIL;
            // Operations, not a rider: push to whoever is on duty, and an
            // email so it is still visible to somebody who was not.
            case VERIFICATION_SUBMITTED -> PUSH_AND_EMAIL;
            case SUPPORT_REPLY -> PUSH_THEN_EMAIL_THEN_SMS;
            case PAYMENT_RECEIPT -> EMAIL;
            // SOS contact texts are sent by SosService itself - to other
            // people's numbers, not through an account's devices.
            case SOS_ALERT -> INBOX_ONLY;
        };
    }
}
