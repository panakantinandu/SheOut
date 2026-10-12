package com.sheout.booking;

/**
 * What the sender decides when her parcel cannot be handed over: the partner
 * waits, leaves it with someone the sender names, or brings it back (the
 * sender pays the one-way fare again).
 */
public enum ParcelSenderChoice {
    WAIT,
    LEAVE_WITH,
    RETURN
}
