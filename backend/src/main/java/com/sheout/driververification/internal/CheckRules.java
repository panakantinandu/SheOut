package com.sheout.driververification.internal;

import com.sheout.driververification.ApplicantProfiles.ApplicantProfile;
import com.sheout.driververification.CheckType;
import com.sheout.driververification.VerificationCheck.Finding;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * The free automatic checks: what software can notice about a check's
 * subject without a vendor. They only ever add findings; whether a finding
 * fails a check or only asks a person to look is CheckFinding's to say.
 */
@Component
public class CheckRules {

    /**
     * What one check is about, as the rules see it. document is null for
     * the ID and selfie, which live on her verification record.
     */
    record Subject(UUID accountId, CheckType type, PartnerDocumentEntity document, ApplicantProfile profile) {
    }

    List<Finding> findings(Subject subject) {
        return List.of();
    }
}
