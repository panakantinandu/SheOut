package com.sheout.booking;

/** Why a parcel could not be handed over at the drop. OTHER needs a note. */
public enum ParcelExceptionReason {
    RECEIVER_NOT_THERE,
    RECEIVER_NOT_ANSWERING,
    RECEIVER_REFUSED,
    RECEIVER_HAS_NO_CODE,
    ADDRESS_NOT_FOUND,
    OTHER;

    public boolean requiresNote() {
        return this == OTHER;
    }
}
