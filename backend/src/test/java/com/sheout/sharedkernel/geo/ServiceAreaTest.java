package com.sheout.sharedkernel.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins where SheOut will and will not work.
 * <p>
 * The case that prompted this: the apps were opened from the United States
 * and behaved as though everything was normal. A partner could tap Go Online
 * from Dallas and be told "Looking for ride requests nearby" - thirteen
 * thousand kilometres from the nearest possible rider.
 */
class ServiceAreaTest {

    /** Production's defaults: 150km around Hyderabad. */
    private static final ServiceArea AREA = new ServiceArea(17.3850, 78.4867, 150, "Hyderabad");

    @Test
    @DisplayName("places around Hyderabad are covered")
    void coversTheCity() {
        assertTrue(AREA.covers(17.3850, 78.4867), "the centre itself");
        assertTrue(AREA.covers(17.4435, 78.3772), "Hitec City");
        assertTrue(AREA.covers(17.4401, 78.3489), "Gachibowli");
        assertTrue(AREA.covers(17.3616, 78.4747), "Charminar");
        assertTrue(AREA.covers(17.2403, 78.4294), "Shamshabad airport");
    }

    @Test
    @DisplayName("nearby towns inside the radius are covered, because the radius is 150km and not the city limits")
    void coversTheWiderRegion() {
        assertTrue(AREA.covers(17.8731, 78.0026), "Sangareddy area");
        assertTrue(AREA.covers(16.7500, 78.1333), "Mahbubnagar, ~90km out");
    }

    @Test
    @DisplayName("far-away places are not covered")
    void refusesFarAway() {
        assertFalse(AREA.covers(32.7767, -96.7970), "Dallas");
        assertFalse(AREA.covers(19.0760, 72.8777), "Mumbai");
        assertFalse(AREA.covers(12.9716, 77.5946), "Bengaluru");
        assertFalse(AREA.covers(28.6139, 77.2090), "Delhi");
        assertFalse(AREA.covers(0, 0), "the null island a broken GPS reports");
    }

    @Test
    @DisplayName("the edge of the radius is the edge, not a suggestion")
    void boundaryIsTheRadius() {
        // Due north of the centre. One degree of latitude is ~111.19km, so
        // these sit a little inside and a little outside 150km.
        double justInside = 17.3850 + (149.0 / 111.19);
        double justOutside = 17.3850 + (151.0 / 111.19);
        assertTrue(AREA.covers(justInside, 78.4867), "149km out");
        assertFalse(AREA.covers(justOutside, 78.4867), "151km out");
    }

    @Test
    @DisplayName("how far outside is reported, because 'outside' alone answers nothing")
    void reportsHowFarOutside() {
        assertEquals(0, AREA.kilometresOutside(17.4435, 78.3772), "inside is not outside");
        // Dallas is roughly 13,700km from Hyderabad; this asserts the order
        // of magnitude, not a figure that would need updating.
        long dallas = AREA.kilometresOutside(32.7767, -96.7970);
        assertTrue(dallas > 13_000 && dallas < 14_500, "Dallas was reported " + dallas + "km outside");
    }

    @Test
    @DisplayName("the boundary moves with config, since it is a business decision and not a constant")
    void boundaryIsConfigurable() {
        ServiceArea wider = new ServiceArea(17.3850, 78.4867, 600, "Hyderabad");
        assertTrue(wider.covers(12.9716, 77.5946), "Bengaluru falls inside a 600km radius");
        assertFalse(AREA.covers(12.9716, 77.5946), "but not inside the 150km one");
    }
}
