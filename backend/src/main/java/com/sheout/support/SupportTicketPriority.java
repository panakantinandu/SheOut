package com.sheout.support;

/**
 * How urgently a ticket should be picked up. Set by the server from the
 * category, never by the person raising it - a priority the raiser chooses
 * is a priority everyone chooses HIGH.
 */
public enum SupportTicketPriority {
    LOW,
    MEDIUM,
    HIGH;

    /** SAFETY_CONCERN is HIGH; everything else starts at MEDIUM. */
    public static SupportTicketPriority forCategory(SupportTicketCategory category) {
        return category == SupportTicketCategory.SAFETY_CONCERN ? HIGH : MEDIUM;
    }
}
