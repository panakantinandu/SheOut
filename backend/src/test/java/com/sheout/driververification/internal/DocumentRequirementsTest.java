package com.sheout.driververification.internal;

import org.junit.jupiter.api.Test;

import static com.sheout.driververification.PartnerDocumentType.DRIVING_LICENCE;
import static com.sheout.driververification.PartnerDocumentType.FITNESS_CERTIFICATE;
import static com.sheout.driververification.PartnerDocumentType.PUC;
import static com.sheout.driververification.PartnerDocumentType.VEHICLE_INSURANCE;
import static com.sheout.driververification.PartnerDocumentType.VEHICLE_RC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What each vehicle needs comes from one map of configuration, and bad configuration stops startup. */
class DocumentRequirementsTest {

    static DocumentRequirements defaults() {
        return new DocumentRequirements("BIKE,PARCEL", "AUTO", "CAB",
                "DRIVING_LICENCE,VEHICLE_RC,VEHICLE_INSURANCE,PUC",
                "DRIVING_LICENCE,VEHICLE_RC,VEHICLE_INSURANCE,PUC,FITNESS_CERTIFICATE",
                "DRIVING_LICENCE,VEHICLE_RC,VEHICLE_INSURANCE,PUC,FITNESS_CERTIFICATE",
                "DRIVING_LICENCE,VEHICLE_RC,VEHICLE_INSURANCE,PUC",
                "BIKE,AUTO,CAB", "30,7,1", true);
    }

    @Test
    void aBikeNeedsLicenceRcInsuranceAndPuc() {
        assertThat(defaults().requiredFor("BIKE")).containsExactly(DRIVING_LICENCE, VEHICLE_RC, VEHICLE_INSURANCE, PUC);
    }

    @Test
    void autosAndCabsAlsoNeedAFitnessCertificate() {
        assertThat(defaults().requiredFor("AUTO")).contains(FITNESS_CERTIFICATE);
        assertThat(defaults().requiredFor("CAB")).contains(FITNESS_CERTIFICATE);
    }

    @Test
    void aVehicleNobodyKnowsGetsTheStrictestList() {
        assertThat(defaults().requiredFor(null)).contains(FITNESS_CERTIFICATE, PUC);
        assertThat(defaults().requiredFor("SPACESHIP")).contains(FITNESS_CERTIFICATE);
    }

    @Test
    void aServiceAddedByConfigAddsItsDocuments() {
        // A bike doing parcels needs whatever parcels need, even if bike rides do not.
        DocumentRequirements r = new DocumentRequirements("BIKE,PARCEL", "AUTO", "CAB",
                "DRIVING_LICENCE", "DRIVING_LICENCE", "DRIVING_LICENCE", "DRIVING_LICENCE,PUC",
                "BIKE", "30", true);
        assertThat(r.requiredFor("BIKE")).containsExactly(DRIVING_LICENCE, PUC);
    }

    @Test
    void passengersNeedACommercialPolicyAndAParcelOnlyVehicleWouldNot() {
        assertThat(defaults().commercialInsuranceRequired("BIKE")).isTrue();
        DocumentRequirements parcelOnly = new DocumentRequirements("PARCEL", "AUTO", "CAB",
                "DRIVING_LICENCE", "DRIVING_LICENCE", "DRIVING_LICENCE", "DRIVING_LICENCE",
                "AUTO,CAB", "30", true);
        assertThat(parcelOnly.commercialInsuranceRequired("BIKE")).isFalse();
        assertThat(parcelOnly.commercialInsuranceRequired("AUTO")).isTrue();
    }

    @Test
    void remindersAreLargestFirst() {
        DocumentRequirements r = new DocumentRequirements("BIKE", "AUTO", "CAB", "PUC", "PUC", "PUC", "PUC",
                "BIKE", "1, 30,7", true);
        assertThat(r.reminderDays()).containsExactly(30, 7, 1);
    }

    @Test
    void anUnknownDocumentNameStopsStartup() {
        assertThatThrownBy(() -> new DocumentRequirements("BIKE", "AUTO", "CAB", "DRIVING_LICENSE", "PUC", "PUC", "PUC",
                "BIKE", "30", true))
                .hasMessageContaining("PARTNER_DOCS_BIKE").hasMessageContaining("DRIVING_LICENSE");
    }

    @Test
    void policeEvidenceIsNotAVehicleDocument() {
        assertThatThrownBy(() -> new DocumentRequirements("BIKE", "AUTO", "CAB", "POLICE_CERTIFICATE", "PUC", "PUC", "PUC",
                "BIKE", "30", true))
                .hasMessageContaining("police check");
    }

    @Test
    void aVehicleCannotBeSentOnAServiceWithNoList() {
        assertThatThrownBy(() -> new DocumentRequirements("BIKE,LUNCHBOX", "AUTO", "CAB", "PUC", "PUC", "PUC", "PUC",
                "BIKE", "30", true))
                .hasMessageContaining("LUNCHBOX");
    }
}
