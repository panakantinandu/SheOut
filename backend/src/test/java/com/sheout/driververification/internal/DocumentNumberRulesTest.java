package com.sheout.driververification.internal;

import com.sheout.driververification.PartnerDocumentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.sheout.driververification.PartnerDocumentType.DRIVING_LICENCE;
import static com.sheout.driververification.PartnerDocumentType.POLICE_CERTIFICATE;
import static com.sheout.driververification.PartnerDocumentType.PUC;
import static com.sheout.driververification.PartnerDocumentType.VEHICLE_INSURANCE;
import static com.sheout.driververification.PartnerDocumentType.VEHICLE_RC;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a reviewer can be asked to check: numbers in the shape the RTO,
 * insurer or police issue, and valid-until dates a document can really
 * carry. Everything else is refused when it is typed.
 */
class DocumentNumberRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private static VerificationError number(PartnerDocumentType type, String n) {
        return DocumentNumberRules.numberProblem(type, n, TODAY);
    }

    @Test
    void aLicenceIsStateRtoYearAndSerialHoweverSheSpacesIt() {
        assertThat(number(DRIVING_LICENCE, "TS0920210012345")).isNull();
        assertThat(number(DRIVING_LICENCE, "ts09 2021 0012345")).isNull();
        assertThat(number(DRIVING_LICENCE, "TS-09-2021-0012345")).isNull();
        assertThat(number(DRIVING_LICENCE, "AP02820140012345")).as("three-digit RTO code").isNull();
        assertThat(DocumentNumberRules.normalize(DRIVING_LICENCE, "ts09 2021-0012345")).isEqualTo("TS0920210012345");
    }

    @Test
    void aLicenceNumberThatCannotBeRealIsRefused() {
        assertThat(number(DRIVING_LICENCE, "1234567")).isEqualTo(VerificationError.LICENCE_NUMBER_INVALID);
        assertThat(number(DRIVING_LICENCE, "TS092021001234")).as("one digit short").isEqualTo(VerificationError.LICENCE_NUMBER_INVALID);
        assertThat(number(DRIVING_LICENCE, "TS09202100123456789")).as("too long").isEqualTo(VerificationError.LICENCE_NUMBER_INVALID);
        assertThat(number(DRIVING_LICENCE, "ZZ0920210012345")).as("no such state").isEqualTo(VerificationError.LICENCE_NUMBER_INVALID);
        assertThat(number(DRIVING_LICENCE, "TS0920300012345")).as("issued in the future").isEqualTo(VerificationError.LICENCE_NUMBER_INVALID);
        assertThat(number(DRIVING_LICENCE, "TS0918000012345")).as("issued in 1800").isEqualTo(VerificationError.LICENCE_NUMBER_INVALID);
        assertThat(number(DRIVING_LICENCE, " ")).isEqualTo(VerificationError.DOCUMENT_NUMBER_REQUIRED);
    }

    @Test
    void anRcIsANumberPlate() {
        assertThat(number(VEHICLE_RC, "TS09AB1234")).isNull();
        assertThat(number(VEHICLE_RC, "ts 09 ab 1234")).isNull();
        assertThat(number(VEHICLE_RC, "22BH1234AB")).isNull();
        assertThat(number(VEHICLE_RC, "TS09AB12345")).isEqualTo(VerificationError.REGISTRATION_NUMBER_INVALID);
        assertThat(number(VEHICLE_RC, "my scooter")).isEqualTo(VerificationError.REGISTRATION_NUMBER_INVALID);
    }

    @Test
    void policyAndCertificateNumbersNeedDigitsAndARealLength() {
        assertThat(number(VEHICLE_INSURANCE, "3005/A/123456789/00/000")).isNull();
        assertThat(number(VEHICLE_INSURANCE, "OG-24-1901-1803-00012345")).isNull();
        assertThat(number(VEHICLE_INSURANCE, "ABCDEFGH")).as("no digits").isEqualTo(VerificationError.POLICY_NUMBER_INVALID);
        assertThat(number(VEHICLE_INSURANCE, "12345")).as("too short").isEqualTo(VerificationError.POLICY_NUMBER_INVALID);
        assertThat(number(VEHICLE_INSURANCE, "1".repeat(31))).as("too long").isEqualTo(VerificationError.POLICY_NUMBER_INVALID);
        assertThat(number(VEHICLE_INSURANCE, "1234#5678")).as("odd characters").isEqualTo(VerificationError.POLICY_NUMBER_INVALID);
        assertThat(number(PUC, "TS00900010012345")).isNull();
        assertThat(number(PUC, "PUC")).isEqualTo(VerificationError.CERTIFICATE_NUMBER_INVALID);
        assertThat(number(POLICE_CERTIFICATE, "TSPVC/2026/77")).isNull();
    }

    @Test
    void aValidUntilDateCannotBeFurtherAheadThanTheDocumentIsIssuedFor() {
        assertThat(DocumentNumberRules.validUntilProblem(PUC, TODAY.plusMonths(11), TODAY)).isNull();
        assertThat(DocumentNumberRules.validUntilProblem(PUC, TODAY.plusYears(2), TODAY)).isEqualTo(VerificationError.VALID_UNTIL_TOO_FAR);
        assertThat(DocumentNumberRules.validUntilProblem(VEHICLE_INSURANCE, TODAY.plusYears(5), TODAY)).isNull();
        assertThat(DocumentNumberRules.validUntilProblem(VEHICLE_INSURANCE, TODAY.plusYears(6), TODAY)).isEqualTo(VerificationError.VALID_UNTIL_TOO_FAR);
        assertThat(DocumentNumberRules.validUntilProblem(VEHICLE_RC, TODAY.plusYears(14), TODAY)).isNull();
        assertThat(DocumentNumberRules.validUntilProblem(DRIVING_LICENCE, LocalDate.of(2205, 1, 1), TODAY))
                .as("a mistyped year").isEqualTo(VerificationError.VALID_UNTIL_TOO_FAR);
        assertThat(DocumentNumberRules.validUntilProblem(POLICE_CERTIFICATE, null, TODAY)).isNull();
    }
}
