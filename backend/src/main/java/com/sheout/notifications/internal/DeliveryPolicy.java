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

    static DeliveryPolicy forType(NotificationType type) {
        return switch (type) {
            // She is looking at the searching screen the moment she books;
            // a push saying so would arrive on top of it.
            case BOOKING_REQUESTED -> INBOX_ONLY;
            case DRIVER_OFFER, SOS_OPERATOR_ALERT -> PUSH_ONLY;
            case BOOKING_ACCEPTED, DRIVER_ARRIVING, BOOKING_COMPLETED, BOOKING_CANCELLED,
                 NO_DRIVERS_AVAILABLE, ACCOUNT_VERIFIED, PAYOUT_PAID -> PUSH_THEN_SMS;
            case SUPPORT_REPLY -> PUSH_THEN_EMAIL_THEN_SMS;
            case PAYMENT_RECEIPT -> EMAIL;
            // SOS contact texts are sent by SosService itself - to other
            // people's numbers, not through an account's devices.
            case SOS_ALERT -> INBOX_ONLY;
        };
    }
}
