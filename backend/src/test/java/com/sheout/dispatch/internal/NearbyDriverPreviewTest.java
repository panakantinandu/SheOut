package com.sheout.dispatch.internal;

import com.sheout.sharedkernel.geo.GeoDistance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the blur on the riders' "partners near you" preview.
 * <p>
 * Two properties carry the privacy guarantee, and a regression in either is
 * silent - the map still shows bikes. The offset must always be 50-100 m, and
 * it must be the same on every poll, because a fresh random offset each time
 * lets anyone average repeated polls back to where a partner really waits.
 */
class NearbyDriverPreviewTest {

    private static final double LAT = 17.4400;
    private static final double LNG = 78.3900;

    @Test
    @DisplayName("every blurred point is 50-100 m from the real one")
    void offsetWithinBounds() {
        NearbyDriverPreview preview = new NearbyDriverPreview();
        for (int i = 0; i < 2000; i++) {
            ApproximatePosition p = preview.blur(UUID.randomUUID(), LAT, LNG);
            double metres = GeoDistance.haversineKm(LAT, LNG, p.lat(), p.lng()) * 1000;
            // Rounding to five decimals moves a point by at most ~1 m.
            assertTrue(metres >= 48 && metres <= 102, "offset " + metres + " m");
        }
    }

    @Test
    @DisplayName("the same partner is blurred to the same point on every poll")
    void stableForRepeatedPolls() {
        NearbyDriverPreview preview = new NearbyDriverPreview();
        UUID partner = UUID.randomUUID();
        ApproximatePosition first = preview.blur(partner, LAT, LNG);
        for (int i = 0; i < 50; i++) {
            assertEquals(first, preview.blur(partner, LAT, LNG));
        }
    }

    @Test
    @DisplayName("two partners at the same spot get different offsets")
    void differentPartnersDiffer() {
        NearbyDriverPreview preview = new NearbyDriverPreview();
        assertNotEquals(preview.blur(UUID.randomUUID(), LAT, LNG), preview.blur(UUID.randomUUID(), LAT, LNG));
    }

    @Test
    @DisplayName("the offset cannot be recomputed by another instance - the key is per process")
    void keyIsPerProcess() {
        UUID partner = UUID.randomUUID();
        assertNotEquals(new NearbyDriverPreview().blur(partner, LAT, LNG), new NearbyDriverPreview().blur(partner, LAT, LNG));
    }
}
