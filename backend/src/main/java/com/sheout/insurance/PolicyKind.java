package com.sheout.insurance;

/**
 * What a master policy covers. PASSENGER_TRIP covers a rider on a ride;
 * GOODS_IN_TRANSIT a parcel on a delivery; the PARTNER_ kinds are group
 * covers partners are enrolled in. Stored by name.
 */
public enum PolicyKind {
    PASSENGER_TRIP,
    PARTNER_HEALTH,
    PARTNER_TERM_LIFE,
    PARTNER_ACCIDENT,
    GOODS_IN_TRANSIT;

    /** A cover a partner is enrolled in, rather than one a trip has. */
    public boolean partnerCover() {
        return this == PARTNER_HEALTH || this == PARTNER_TERM_LIFE || this == PARTNER_ACCIDENT;
    }
}
