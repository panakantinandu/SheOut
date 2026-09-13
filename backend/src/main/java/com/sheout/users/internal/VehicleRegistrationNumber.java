package com.sheout.users.internal;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks that a vehicle registration number is structurally a real Indian
 * plate.
 * <p>
 * FORMAT ONLY. This says the number is shaped like a plate the RTO could
 * have issued and carries a state code that exists. It says nothing about
 * whether the vehicle exists, whether it is this partner's, or whether it
 * is roadworthy. Nothing here can establish any of that, and pretending
 * otherwise would be worse than not checking at all - an operator who
 * believes a green tick means "verified vehicle" stops looking at the
 * document. What closes that gap is the RC photo sitting next to this
 * number in the admin review queue, read by a person.
 * <p>
 * Worth doing anyway: a typo, or a plate somebody invented, is caught at
 * the moment it is typed rather than by a rider staring at a scooter whose
 * number does not match her screen.
 */
public final class VehicleRegistrationNumber {

    /**
     * The standard format: state code, RTO code, series, serial.
     * <p>
     * TS06FH2653 and KA05MH1234 are both this shape. The RTO code is one or
     * two digits and the series one to three letters, because both genuinely
     * vary - older and smaller RTOs issue shorter ones.
     * <p>
     * Three letters, not two, because of Delhi. A plate like DL3CAB1234 puts
     * a vehicle-class letter immediately after the RTO number, so the letters
     * between the digits run to three. A two-letter cap looks right against
     * TS06FH2653 and refuses every Delhi taxi on the road - which a test
     * caught, and a regex written from two example plates would not have.
     */
    private static final Pattern STANDARD = Pattern.compile("^([A-Z]{2})\\d{1,2}[A-Z]{1,3}\\d{4}$");

    /**
     * The BH ("Bharat") series, introduced in 2021 for vehicles that move
     * between states with their owner - defence, central government, and
     * private employers with offices in several states. Shaped completely
     * differently: year first, then BH, then the serial, then the series
     * letters. 22BH1234AB.
     * <p>
     * It carries no state code at all, by design - that is the entire point
     * of it - so the state check below must not be applied to one.
     */
    private static final Pattern BH_SERIES = Pattern.compile("^\\d{2}BH\\d{4}[A-Z]{1,2}$");

    /**
     * Every state and union territory code currently issued, plus the
     * legacy codes still on the road.
     * <p>
     * A format-only regex would accept ZZ01AB1234 quite happily, which is
     * why this list exists: "two letters" is not a state.
     * <p>
     * The legacy codes are deliberately kept. OR was Odisha before OD, UA
     * was Uttarakhand before UK, DN and DD were separate before the 2020
     * merger, and TS is Telangana's code on virtually every plate issued
     * since 2014 even though TG became the official one in 2024. Those
     * vehicles are still being driven. Rejecting them would turn a real
     * plate into a data-entry error a partner cannot get past, and she
     * would be locked out of working over a code change she has nothing to
     * do with.
     */
    private static final Set<String> STATE_CODES = Set.of(
            "AN", // Andaman and Nicobar Islands
            "AP", // Andhra Pradesh
            "AR", // Arunachal Pradesh
            "AS", // Assam
            "BR", // Bihar
            "CG", // Chhattisgarh
            "CH", // Chandigarh
            "DD", // Dadra and Nagar Haveli and Daman and Diu
            "DL", // Delhi
            "DN", // legacy - Dadra and Nagar Haveli, before the 2020 merger
            "GA", // Goa
            "GJ", // Gujarat
            "HP", // Himachal Pradesh
            "HR", // Haryana
            "JH", // Jharkhand
            "JK", // Jammu and Kashmir
            "KA", // Karnataka
            "KL", // Kerala
            "LA", // Ladakh
            "LD", // Lakshadweep
            "MH", // Maharashtra
            "ML", // Meghalaya
            "MN", // Manipur
            "MP", // Madhya Pradesh
            "MZ", // Mizoram
            "NL", // Nagaland
            "OD", // Odisha
            "OR", // legacy - Odisha, before the rename to OD
            "PB", // Punjab
            "PY", // Puducherry
            "RJ", // Rajasthan
            "SK", // Sikkim
            "TG", // Telangana - the official code since 2024
            "TN", // Tamil Nadu
            "TR", // Tripura
            "TS", // Telangana - on nearly every plate issued since 2014
            "UA", // legacy - Uttarakhand, before the rename to UK
            "UK", // Uttarakhand
            "UP", // Uttar Pradesh
            "WB"  // West Bengal
    );

    private VehicleRegistrationNumber() {
    }

    /**
     * Strips spaces and hyphens and upper-cases, so "ts 06 fh 2653" and
     * "TS-06-FH-2653" both become TS06FH2653.
     * <p>
     * People write plates the way they read them, with the groups spaced
     * out. Refusing that would be refusing the correct answer because of
     * its punctuation.
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\s-]", "").toUpperCase();
    }

    /** True if this is a well-formed plate of either kind, with a real state code where one applies. */
    public static boolean isValid(String raw) {
        String normalized = normalize(raw);
        if (BH_SERIES.matcher(normalized).matches()) {
            return true;
        }
        var match = STANDARD.matcher(normalized);
        if (!match.matches()) {
            return false;
        }
        return STATE_CODES.contains(match.group(1));
    }

    /**
     * Why a number was refused, in words a partner can act on.
     * <p>
     * Three different messages rather than one, because "invalid" tells
     * somebody staring at a plate they have copied correctly precisely
     * nothing. A wrong state code and a wrong shape are different mistakes
     * and need different corrections.
     */
    public static String rejectionReason(String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) {
            return "Enter your vehicle's registration number, as it appears on the number plate.";
        }
        var match = STANDARD.matcher(normalized);
        if (match.matches() && !STATE_CODES.contains(match.group(1))) {
            return "\"%s\" is not an Indian state code. Check the first two letters - for example TS06FH2653."
                    .formatted(match.group(1));
        }
        return "That does not look like an Indian registration number. Use the format on your number plate, "
                + "for example TS06FH2653, or 22BH1234AB for a Bharat-series plate.";
    }
}
