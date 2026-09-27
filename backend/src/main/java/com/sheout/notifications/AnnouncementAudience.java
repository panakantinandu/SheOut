package com.sheout.notifications;

/** Who a broadcast goes to. */
public enum AnnouncementAudience {
    ALL_CUSTOMERS,
    ALL_DRIVERS,
    BOTH,
    /** Everyone who tapped "Notify me" on the SheOut Seller coming-soon screen. */
    SELLER_WAITLIST
}
