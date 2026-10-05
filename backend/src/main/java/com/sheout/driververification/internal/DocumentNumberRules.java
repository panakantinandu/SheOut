package com.sheout.driververification.internal;

import com.sheout.driververification.PartnerDocumentType;
import com.sheout.sharedkernel.india.VehicleRegistrationNumber;

import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a document number and its dates may look like, by document, before a
 * person is asked to check them against the photo.
 * <p>
 * WHY STRICT. A reviewer compares what she typed with what the photo shows.
 * "1234567" for a licence, or a plate typed as "my scooter", gives them
 * nothing to compare - so it is refused when it is typed, with the shape it
 * should have, rather than becoming a review nobody can finish. The same
 * rules apply to an operator's correction, so a review cannot approve a
 * number in a shape no RTO issues either.
 * <p>
 * FORMAT ONLY. Like VehicleRegistrationNumber, passing here means "shaped
 * like the real thing", never "this licence exists". The person reading the
 * photo is still the check.
 * <ul>
 *   <li>Driving licence: the national (Sarathi) form, state code + RTO code +
 *       year of issue + serial: TS09 2021 0012345, 15 characters. A
 *       three-digit RTO code (16 characters) is accepted too, because some
 *       states issue them. The state must be real and the year not in the
 *       future.</li>
 *   <li>Vehicle RC: the number plate - see VehicleRegistrationNumber.</li>
 *   <li>Insurance policy, PUC, fitness and police certificates: there is no
 *       one national format (every insurer and state numbers its own), so
 *       letters, digits, "/" and "-", with at least one digit, within a
 *       length no real one exceeds.</li>
 * </ul>
 * Dates: a valid-until date may not be further ahead than the longest such
 * a document is issued for, which catches a year typed as 2205 or a date
 * copied from the wrong line - an RC registration (15 years) is not a PUC
 * (at most a year).
 */
final class DocumentNumberRules {

    private static final Pattern LICENCE = Pattern.compile("^([A-Z]{2})(\\d{2,3})(\\d{4})(\\d{7})$");
    private static final Pattern REFERENCE = Pattern.compile("^[A-Z0-9][A-Z0-9/-]*$");

    private DocumentNumberRules() {
    }

    /** As stored and compared: upper case, without spaces; licences and plates also without hyphens. */
    static String normalize(PartnerDocumentType type, String raw) {
        if (raw == null) {
            return "";
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return switch (type) {
            case DRIVING_LICENCE, VEHICLE_RC -> upper.replace("-", "");
            default -> upper;
        };
    }

    /** Null when the number is in an acceptable shape for this document, otherwise why not. */
    static VerificationError numberProblem(PartnerDocumentType type, String raw, LocalDate today) {
        String n = normalize(type, raw);
        if (n.isEmpty()) {
            return VerificationError.DOCUMENT_NUMBER_REQUIRED;
        }
        return switch (type) {
            case DRIVING_LICENCE -> licenceOk(n, today) ? null : VerificationError.LICENCE_NUMBER_INVALID;
            case VEHICLE_RC -> VehicleRegistrationNumber.isValid(n) ? null : VerificationError.REGISTRATION_NUMBER_INVALID;
            case VEHICLE_INSURANCE -> referenceOk(n, 6, 30) ? null : VerificationError.POLICY_NUMBER_INVALID;
            case PUC, FITNESS_CERTIFICATE -> referenceOk(n, 6, 25) ? null : VerificationError.CERTIFICATE_NUMBER_INVALID;
            case POLICE_CERTIFICATE, BACKGROUND_CHECK_REPORT -> referenceOk(n, 4, 30) ? null : VerificationError.CERTIFICATE_NUMBER_INVALID;
        };
    }

    /** The furthest ahead a valid-until date can be for this document, from today; null for one with no such date. */
    static Period longestValidity(PartnerDocumentType type) {
        return switch (type) {
            // Up to 20 years for a private licence (less with age); a transport one is 5.
            case DRIVING_LICENCE -> Period.ofYears(20);
            // A new private vehicle is registered for 15 years; renewals are 5.
            case VEHICLE_RC -> Period.ofYears(15);
            // A commercial policy is yearly; a new two-wheeler's third-party cover runs 5 years.
            case VEHICLE_INSURANCE -> Period.ofYears(5).plusDays(1);
            // A PUC certificate is valid for at most a year.
            case PUC -> Period.ofYears(1).plusDays(1);
            // A transport vehicle's fitness is renewed every one or two years.
            case FITNESS_CERTIFICATE -> Period.ofYears(2).plusDays(1);
            case POLICE_CERTIFICATE, BACKGROUND_CHECK_REPORT -> null;
        };
    }

    /** Null when the valid-until date is believable for this document, otherwise why not. */
    static VerificationError validUntilProblem(PartnerDocumentType type, LocalDate validUntil, LocalDate today) {
        Period longest = longestValidity(type);
        if (validUntil == null || longest == null) {
            return null;
        }
        return validUntil.isAfter(today.plus(longest)) ? VerificationError.VALID_UNTIL_TOO_FAR : null;
    }

    private static boolean licenceOk(String n, LocalDate today) {
        Matcher m = LICENCE.matcher(n);
        if (!m.matches() || !VehicleRegistrationNumber.isStateCode(m.group(1))) {
            return false;
        }
        int year = Integer.parseInt(m.group(3));
        return year >= 1950 && year <= today.getYear();
    }

    private static boolean referenceOk(String n, int min, int max) {
        return n.length() >= min && n.length() <= max && REFERENCE.matcher(n).matches() && n.chars().anyMatch(Character::isDigit);
    }
}
