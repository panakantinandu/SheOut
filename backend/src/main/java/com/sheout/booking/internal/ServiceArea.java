package com.sheout.booking.internal;

import com.sheout.booking.GeoAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where SheOut will actually pick you up and drop you off: a centre point
 * and a radius, both configurable.
 * <p>
 * Configurable rather than hardcoded because the boundary is a business
 * decision that will move. Widening to 250km, or re-centring on a second
 * city, is an environment variable and a restart - not a code change, a
 * review and a deploy. See application.yml for the keys.
 * <p>
 * Enforced in the service, not the controller, and so on both the booking
 * and the quote path. A check that only runs in the browser is a
 * suggestion: anyone can post coordinates straight at the API, and the
 * frontend's own inline warning exists to be helpful, not to be the gate.
 * <p>
 * Straight-line distance from the centre, via {@link GeoDistance} - the
 * same measurement the fare is priced on, so a trip can never be quoted on
 * one definition of distance and admitted on another.
 */
@Component
public class ServiceArea {

    private static final Logger log = LoggerFactory.getLogger(ServiceArea.class);

    private final GeoAddress centre;
    private final double radiusKm;
    private final String centreName;

    ServiceArea(
            @Value("${sheout.booking.service-area.centre-lat:17.3850}") double centreLat,
            @Value("${sheout.booking.service-area.centre-lng:78.4867}") double centreLng,
            @Value("${sheout.booking.service-area.radius-km:150}") double radiusKm,
            @Value("${sheout.booking.service-area.centre-name:Hyderabad}") String centreName) {
        this.centre = new GeoAddress(centreName, centreLat, centreLng);
        this.radiusKm = radiusKm;
        this.centreName = centreName;
        log.info("Service area: within {}km of {} ({}, {})", radiusKm, centreName, centreLat, centreLng);
    }

    /** True when this point is somewhere SheOut operates. */
    public boolean covers(GeoAddress point) {
        return distanceFromCentreKm(point) <= radiusKm;
    }

    public double distanceFromCentreKm(GeoAddress point) {
        return GeoDistance.haversineKm(centre, point);
    }

    public double radiusKm() {
        return radiusKm;
    }

    public String centreName() {
        return centreName;
    }
}
