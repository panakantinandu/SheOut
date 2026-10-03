package com.sheout.driververification;

/**
 * Where one of a partner's documents stands.
 * <p>
 * PENDING is a row with nothing to look at yet - a background check sent to
 * a provider and not yet answered. A document she uploads goes straight to
 * UNDER_REVIEW, the same collapse VerificationStatus makes. EXPIRED is set
 * by the expiry sweep at valid_until, never by an operator.
 */
public enum PartnerDocumentStatus {
    PENDING,
    UNDER_REVIEW,
    VERIFIED,
    REJECTED,
    EXPIRED
}
