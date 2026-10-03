package com.sheout.support;

/** What a ticket is about, chosen by the person raising it. */
public enum SupportTicketCategory {
    PAYMENT_DISPUTE,
    DRIVER_OR_CUSTOMER_BEHAVIOR,
    /**
     * Always HIGH priority, set by the server - see SupportTicketPriority.
     * <p>
     * Not an emergency channel. A ticket is read when an operator reaches it;
     * someone in danger right now needs SOS or 112, and both apps say so
     * where this category is chosen.
     */
    SAFETY_CONCERN,
    APP_ISSUE,
    CANCELLATION_DISPUTE,
    OTHER,
    /**
     * "Report an accident / make a claim" on a trip - raised by the insurance
     * module, linked to the trip and its cover. HIGH priority, like a safety
     * concern: somebody may be hurt. SheOut helps with the claim; the insurer
     * decides it.
     */
    ACCIDENT_OR_INSURANCE_CLAIM
}
