package com.sheout.sharedkernel.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Where SheOut actually operates: a centre point and a radius, both
 * configurable.
 * <p>
 * Configurable rather than hardcoded because the boundary is a business
 * decision that will move. Widening to 250km, or re-centring on a second
 * city, is an environment variable and a restart - not a code change, a
 * review and a deploy. See application.yml for the keys.
 * <p>
 * IN SHARED KERNEL BECAUSE IT IS NOT A BOOKING RULE. It reads like one -
 * it started life inside booking, gating pickup and drop - but the boundary
 * is a fact about the company, and two modules now have to answer questions
 * against it:
 * <ul>
 *   <li>booking: may this trip be requested? Decided on the PICKUP and DROP,
 *       not on where the phone is. Somebody in another country booking a
 *       ride for their mother in Hyderabad is a real customer, not an
 *       error.</li>
 *   <li>users: may this partner go online? Decided on where the PHONE is,
 *       because the partner is the vehicle. A partner in Dallas reporting
 *       for work in Hyderabad is not a real case.</li>
 * </ul>
 * Booking owning this would have meant users depending on booking for a
 * question booking has no stake in.
 * <p>
 * Enforced in services, never only in a controller or a browser. A check
 * that runs in the browser is a suggestion: anyone can post coordinates
 * straight at the API, and the apps' inline warnings exist to be helpful,
 * not to be the gate.
 * <p>
 * Straight-line distance from the centre, via {@link GeoDistance} - the same
 * measurement the fare is priced on, so a trip can never be quoted on one
 * definition of distance and admitted on another.
 */
@Component
public class ServiceArea {

    private static final Logger log = LoggerFactory.getLogger(ServiceArea.class);

    private final double centreLat;
    private final double centreLng;
    private final double radiusKm;
    private final String centreName;

    ServiceArea(
            @Value("${sheout.booking.service-area.centre-lat:17.3850}") double centreLat,
            @Value("${sheout.booking.service-area.centre-lng:78.4867}") double centreLng,
            @Value("${sheout.booking.service-area.radius-km:150}") double radiusKm,
            @Value("${sheout.booking.service-area.centre-name:Hyderabad}") String centreName) {
        this.centreLat = centreLat;
        this.centreLng = centreLng;
        this.radiusKm = radiusKm;
        this.centreName = centreName;
        log.info("Service area: within {}km of {} ({}, {})", radiusKm, centreName, centreLat, centreLng);
    }

    /** True when this point is somewhere SheOut operates. */
    public boolean covers(double lat, double lng) {
        return distanceFromCentreKm(lat, lng) <= radiusKm;
    }

    public double distanceFromCentreKm(double lat, double lng) {
        return GeoDistance.haversineKm(centreLat, centreLng, lat, lng);
    }

    public double radiusKm() {
        return radiusKm;
    }

    public String centreName() {
        return centreName;
    }

    /**
     * How far outside the boundary a point is, rounded to whole kilometres,
     * for telling somebody why they were refused.
     * <p>
     * A bare "you are outside our service area" invites the reply "by how
     * much?", and the answer matters: fifteen kilometres over is somebody
     * who might drive in, and thirteen thousand is somebody on another
     * continent who should be told plainly that this app is not for where
     * they are standing.
     */
    public long kilometresOutside(double lat, double lng) {
        return Math.max(0, Math.round(distanceFromCentreKm(lat, lng) - radiusKm));
    }
}
