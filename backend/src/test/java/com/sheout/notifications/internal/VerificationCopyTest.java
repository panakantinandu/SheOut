package com.sheout.notifications.internal;

import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.RejectionReason;
import com.sheout.users.AppLanguage;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every reason a person can send something back for is told to her in her
 * own language, and the English push says what the record stores - so the
 * screen and the notification never disagree.
 */
class VerificationCopyTest {

    private final NotificationCopy copy = new NotificationCopy(null);

    /** The short names RejectionReason.message uses. */
    private static final Map<PartnerDocumentType, String> SHORT = Map.of(
            PartnerDocumentType.DRIVING_LICENCE, "licence",
            PartnerDocumentType.VEHICLE_RC, "RC",
            PartnerDocumentType.VEHICLE_INSURANCE, "policy",
            PartnerDocumentType.PUC, "PUC certificate",
            PartnerDocumentType.FITNESS_CERTIFICATE, "fitness certificate",
            PartnerDocumentType.POLICE_CERTIFICATE, "police certificate",
            PartnerDocumentType.BACKGROUND_CHECK_REPORT, "report");

    @Test
    void everyRejectionReasonIsWrittenInEveryLanguage() {
        for (RejectionReason reason : RejectionReason.values()) {
            for (AppLanguage language : AppLanguage.values()) {
                assertThat(copy.one(language, "rejection." + reason.name())).as(reason + " in " + language)
                        .isNotEqualTo("rejection." + reason.name()).doesNotContain("—");
            }
        }
    }

    @Test
    void theEnglishSaysWhatTheRecordStores() {
        for (RejectionReason reason : RejectionReason.values()) {
            String english = copy.one(AppLanguage.EN, "rejection." + reason.name());
            if (reason.forIdentity()) {
                assertThat(english).as(reason.name()).isEqualTo(reason.message(null));
                continue;
            }
            PartnerDocumentType type = Arrays.stream(PartnerDocumentType.values()).filter(reason::appliesTo).findFirst().orElseThrow();
            assertThat(english.replace("{document}", SHORT.get(type))).as(reason.name()).isEqualTo(reason.message(type));
        }
    }

    @Test
    void everyStageIsWrittenInEveryLanguage() {
        for (String key : new String[]{"stageIdReceivedRider", "stageIdReceivedPartner", "stageIdentityApproved",
                "stageVehicleReceived", "stageVehicleApproved", "stageSafetyReceived"}) {
            for (AppLanguage language : AppLanguage.values()) {
                assertThat(copy.one(language, key + ".title")).as(key + " in " + language).isNotEqualTo(key + ".title");
                assertThat(copy.one(language, key + ".body")).as(key + " in " + language).isNotEqualTo(key + ".body");
            }
        }
    }
}
