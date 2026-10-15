package com.sheout.driververification;

/**
 * Why a screening ended in a rejection - a category, never the case itself,
 * so the record says why without holding court details longer than needed.
 */
public enum CourtRejectionCategory {
    /** A record that two reviewers agree is hers, and that bars her from carrying passengers. */
    RECORD_CONFIRMED,
    /** Who she is could not be established well enough to clear her. */
    IDENTITY_UNCLEAR,
    /** She was told and did not answer in time. */
    NO_RESPONSE,
    /** A police clearance certificate was asked for and not provided. */
    PCC_NOT_PROVIDED,
    OTHER
}
