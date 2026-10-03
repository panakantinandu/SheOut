package com.sheout.insurance.internal;

/**
 * The part of an address an insurer needs to place a trip - the locality
 * and city - without her house number.
 * <p>
 * Addresses here are free text from a geocoder or from her ("Flat 302,
 * Lake View Apts, Road No. 3, Banjara Hills, Hyderabad"). The last two
 * comma-separated parts are almost always the locality and city; anything
 * with a digit in it (a flat, a plot, a road number, a PIN code) is dropped.
 * A heuristic, flagged in the README: good enough to place a claim, never
 * the full address.
 */
final class AreaName {

    private AreaName() {
    }

    static String coarse(String label) {
        // One heuristic for every "area, not the house" in SheOut - the
        // console's masking uses the same one.
        return com.sheout.sharedkernel.privacy.Masking.area(label);
    }
}
