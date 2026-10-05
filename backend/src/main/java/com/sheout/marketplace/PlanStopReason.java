package com.sheout.marketplace;

/**
 * Why a seller stopped her monthly plan, when she says - one tap, optional.
 * Read in the console to learn why shops leave; never shown to buyers.
 * <p>
 * Stored by name; never rename a value while rows carry it.
 */
public enum PlanStopReason {
    /** Not enough buyers called or messaged. */
    FEW_ENQUIRIES,
    /** The monthly fee is too much for what she sells. */
    TOO_COSTLY,
    /** She has stopped selling, or is taking a break. */
    STOPPED_SELLING,
    /** She sells somewhere else instead. */
    SELLING_ELSEWHERE,
    OTHER
}
