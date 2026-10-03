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
    static final DeliveryPolicy PUSH_THEN_EMAIL = new DeliveryPolicy(true, true, false, false);
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
            // Good news, already in her balance and her inbox: not worth a paid text.
            case INCENTIVE_EARNED -> PUSH_ONLY;
            // Good news too, and nothing she must act on: not worth a text either.
            case REFERRAL_JOINED, REFERRAL_REWARDED -> PUSH_ONLY;
            // A decision about her shop, or a launch she asked to hear
            // about: worth an email if push did not reach her, not a paid text.
            case SELLER_STATUS, ANNOUNCEMENT -> PUSH_THEN_EMAIL;
            // Mid-trip, both of them in the app, and a question that is
            // stale in two minutes: a text arriving later would only confuse.
            case DESTINATION_CHANGE_REQUESTED, DESTINATION_CHANGED, DESTINATION_CHANGE_DECLINED -> PUSH_ONLY;
            case BOOKING_ACCEPTED, DRIVER_ARRIVING, BOOKING_COMPLETED, BOOKING_CANCELLED,
                 NO_DRIVERS_AVAILABLE, PAYOUT_PAID -> PUSH_THEN_SMS;
            // The answer to "am I allowed to use this app yet", after a wait
            // on a person. Push and email both, not email only when push
            // failed: she may have said no to notifications, or have the app
            // closed for a day, and the whole point is that she does not have
            // to keep coming back to check. Rare enough that always emailing
            // costs nothing.
            case ACCOUNT_VERIFIED, ACCOUNT_VERIFICATION_REJECTED -> PUSH_AND_EMAIL;
            // The same kind of answer, about a change to her details: push, and email if that did not reach her.
            case PROFILE_CHANGE_DECIDED -> PUSH_THEN_EMAIL;
            // Her documents: whether she can work tomorrow. A reminder weeks
            // ahead is worth an email if push missed her; an expiry or a
            // rejection stops her earning, so it is pushed and emailed both,
            // like the verification answer itself.
            case PARTNER_DOCUMENT_EXPIRING -> PUSH_THEN_EMAIL;
            case PARTNER_DOCUMENT_EXPIRED, PARTNER_DOCUMENT_REJECTED -> PUSH_AND_EMAIL;
            // Operators on duty, like an SOS: push, every device.
            case TRIP_WATCH_ALERT -> PUSH_ONLY;
            case STAFF_SECURITY_ALERT -> PUSH_ONLY;
            // "Are you all right?" mid-trip. If push cannot reach her phone
            // - no data - a text is exactly when it matters.
            case TRIP_CHECK_IN -> PUSH_THEN_SMS;
            // She is watching the search screen; a push is enough.
            case PARTNER_LEFT -> PUSH_ONLY;
            // Operations, not a rider: push to whoever is on duty, and an
            // email so it is still visible to somebody who was not.
            case VERIFICATION_SUBMITTED -> PUSH_AND_EMAIL;
            case SUPPORT_REPLY -> PUSH_THEN_EMAIL_THEN_SMS;
            case PAYMENT_RECEIPT -> EMAIL;
            // SOS contact texts are sent by SosService itself - to other
            // people's numbers, not through an account's devices.
            case SOS_ALERT -> INBOX_ONLY;
            // A reminder, not news: a push she can swipe away, never a paid text
            // or an email - a reminder that costs her attention elsewhere is spam.
            case REENGAGEMENT -> PUSH_ONLY;
        };
    }
}
