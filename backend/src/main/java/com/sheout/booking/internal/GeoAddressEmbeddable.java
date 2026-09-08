package com.sheout.booking.internal;

import com.sheout.booking.GeoAddress;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * JPA-mapped mirror of the public {@link GeoAddress} record. Kept as a
 * separate mutable class rather than embedding the record directly -
 * Hibernate's record support for @Embeddable isn't something to bet on
 * without a build to verify against; this is the same reliable
 * entity-vs-DTO split used everywhere else in this codebase.
 */
@Embeddable
public class GeoAddressEmbeddable {

    @Column(length = 255)
    private String label;

    private double lat;

    private double lng;

    protected GeoAddressEmbeddable() {
        // JPA
    }

    public GeoAddressEmbeddable(String label, double lat, double lng) {
        this.label = label;
        this.lat = lat;
        this.lng = lng;
    }

    public static GeoAddressEmbeddable from(GeoAddress address) {
        return new GeoAddressEmbeddable(address.label(), address.lat(), address.lng());
    }

    public GeoAddress toGeoAddress() {
        return new GeoAddress(label, lat, lng);
    }

    public String getLabel() {
        return label;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}
