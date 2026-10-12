package com.sheout.notifications.internal;

/**
 * What a notification is about. Drives the copy (NotificationEventListeners),
 * how it is delivered (DeliveryPolicy) and where tapping it goes.
 * <p>
 * Stored by name in notification_log, so a value is never renamed or removed
 * while rows carry it.
 */
public enum NotificationType {
    /** No longer sent (the search is on the booking screen, not news); kept because the name is stored. */
    BOOKING_REQUESTED,
    /** SheOut gave her money back - into her wallet, or to her UPI or bank. Push and SMS, always. */
    REFUND_ISSUED,
    BOOKING_ACCEPTED,
    /** The partner is close to the pickup - see dispatch's DriverArriving. */
    DRIVER_ARRIVING,
    BOOKING_COMPLETED,
    BOOKING_CANCELLED,
    /** Dispatch searched and nobody took the trip. */
    NO_DRIVERS_AVAILABLE,
    ACCOUNT_VERIFIED,

    /** Her document was looked at and turned down, with the reason. */
    ACCOUNT_VERIFICATION_REJECTED,

    /** To operations: somebody is waiting on a review. */
    VERIFICATION_SUBMITTED,
    /** To her: a stage of her verification moved forward - we have it, or it is approved and here is what is next. */
    VERIFICATION_PROGRESS,
    /** To her: her start-of-shift selfie is held for a person, who looks and when. */
    SHIFT_CHECK_HELD,
    /** To operations: a partner's start-of-shift check is held and she cannot work until somebody looks. */
    SHIFT_CHECK_HELD_STAFF,
    /** The rider's own record of an SOS she raised and who it reached. */
    SOS_ALERT,
    /** An SOS raised by a rider, sent to operators. */
    SOS_OPERATOR_ALERT,
    /** An SOS nobody has taken yet, sent again to everyone who can respond until someone does. */
    SOS_ESCALATION,
    /** An operator replied to a support ticket - see support's SupportReplyPosted. */
    SUPPORT_REPLY,
    /** A trip was offered to this partner - see dispatch's DriverOffered. */
    DRIVER_OFFER,
    PAYOUT_PAID,
    /** A campaign bonus reached her wallet - see campaigns' DriverIncentiveAwarded. */
    INCENTIVE_EARNED,
    PAYMENT_RECEIPT,
    /** To the partner: her rider has asked, mid-trip, to go somewhere else - see booking's DestinationChangeRequested. */
    DESTINATION_CHANGE_REQUESTED,
    /** To both: the partner agreed and the trip now goes to the new drop. */
    DESTINATION_CHANGED,
    /** To the rider: her partner said no; the trip goes on as booked. */
    DESTINATION_CHANGE_DECLINED,
    /** To the one who shared her code: a friend has signed up with it - see campaigns' ReferralJoined. */
    REFERRAL_JOINED,
    /** To a rider: a referral paid out ride credit to her - see campaigns' ReferralCompleted. */
    REFERRAL_REWARDED,
    /** To a seller: her shop was approved, rejected, went live or was suspended - see marketplace's SellerStatusChanged. */
    SELLER_STATUS,
    /** To a seller: her month ends soon, has ended, or her shop is hidden until she pays - see marketplace's SellerPlanNotice. */
    SELLER_PLAN,
    /** To a seller: a SheOut parcel delivered her product and her stock went down, or buyers asked about it today - see marketplace's SellerStockNotice. */
    SELLER_STOCK,
    /** An operator's announcement delivered to each account one by one (the SheOut Seller waitlist), not by topic. */
    ANNOUNCEMENT,

    /**
     * "We miss you": to a rider or partner who has not opened SheOut for a week
     * or more - see ReengagementNudger. Push only, never a paid text or an email.
     */
    REENGAGEMENT,

    PROFILE_CHANGE_DECIDED,

    TRIP_WATCH_ALERT,

    /**
     * To an owner: something in the console needs her attention now - an
     * account locked by failed sign-ins, a role changed, an export, a burst of
     * reveals, a new browser, the audit log failing its check. Push only:
     * email goes separately (StaffEmailApi) and the alert is in the audit log.
     */
    STAFF_SECURITY_ALERT,

    TRIP_CHECK_IN,

    PARTNER_LEFT,

    /** To a partner: one of her documents runs out in 30, 7 or 1 days - see driver-verification's PartnerDocumentExpiring. */
    PARTNER_DOCUMENT_EXPIRING,

    /** To a partner: a document expired, or her police check came due again - see VerificationLapsed. */
    PARTNER_DOCUMENT_EXPIRED,

    /** To a partner: an operator turned one of her documents down, with the reason. */
    PARTNER_DOCUMENT_REJECTED,

    /** To a sender: her partner could not hand the parcel over, and is waiting for her to choose what happens. */
    PARCEL_EXCEPTION
}
