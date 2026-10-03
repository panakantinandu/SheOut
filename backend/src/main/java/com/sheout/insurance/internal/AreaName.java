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
        if (label == null || label.isBlank()) {
            return null;
        }
        String[] parts = label.split(",");
        StringBuilder out = new StringBuilder();
        int kept = 0;
        for (int i = parts.length - 1; i >= 0 && kept < 2; i--) {
            String part = parts[i].trim();
            if (part.isEmpty() || part.chars().anyMatch(Character::isDigit) || part.equalsIgnoreCase("India")) {
                continue;
            }
            out.insert(0, kept == 0 ? part : part + ", ");
            kept++;
        }
        String area = out.toString();
        return area.isEmpty() ? null : area.length() > 200 ? area.substring(0, 200) : area;
    }
}
