package com.sheout.driververification.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sheout.driververification.ApplicantProfiles;
import com.sheout.driververification.ApplicantProfiles.ApplicantProfile;
import com.sheout.driververification.CheckFinding;
import com.sheout.driververification.CheckType;
import com.sheout.driververification.InsuranceUseType;
import com.sheout.driververification.PartnerDocumentStatus;
import com.sheout.driververification.PartnerDocumentType;
import com.sheout.driververification.VerificationCheck.Finding;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The free automatic checks: what software can notice without a vendor.
 * <ul>
 *   <li>Age: 18 or older by the date of birth on her profile.</li>
 *   <li>Expiry: the licence, RC or policy is still in date.</li>
 *   <li>Name: the name she typed for her licence against her profile, with
 *       NameMatcher's tolerance for how Indian names are written. "Same as
 *       my profile" is her declaration, recorded as one and never compared,
 *       because a value we filled in proves nothing.</li>
 *   <li>Duplicates: the same licence or plate on another account. A shared
 *       family scooter is real, so this asks a person and never fails.</li>
 *   <li>Previously rejected: a number a person turned down for fraud or a
 *       confirmed mismatch (FlaggedDocumentNumberEntity).</li>
 *   <li>Photo quality: what her phone said when she chose "Use anyway".</li>
 * </ul>
 * They only ever add findings. Whether one fails a check or only asks a
 * person to look is CheckFinding's to say, and nothing here rejects anybody.
 */
@Component
public class CheckRules {

    static final String META_NAME_ON_DOCUMENT = "nameOnDocument";
    static final String META_NAME_DECLARED = "nameDeclaredSameAsProfile";
    static final String META_PHOTO_WARNINGS = "photoWarnings";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final PartnerDocumentRepository documents;
    private final FlaggedDocumentNumberRepository flagged;
    private final ObjectProvider<ApplicantProfiles> profiles;
    private final Clock clock;

    @Autowired
    CheckRules(PartnerDocumentRepository documents, FlaggedDocumentNumberRepository flagged,
               ObjectProvider<ApplicantProfiles> profiles) {
        this(documents, flagged, profiles, Clock.systemUTC());
    }

    CheckRules(PartnerDocumentRepository documents, FlaggedDocumentNumberRepository flagged,
               ObjectProvider<ApplicantProfiles> profiles, Clock clock) {
        this.documents = documents;
        this.flagged = flagged;
        this.profiles = profiles;
        this.clock = clock;
    }

    /**
     * What one check is about, as the rules see it. document is null for
     * the ID and selfie, which live on her verification record.
     */
    record Subject(UUID accountId, CheckType type, PartnerDocumentEntity document, ApplicantProfile profile,
                   VerificationRecordEntity record) {
    }

    List<Finding> findings(Subject subject) {
        List<Finding> out = new ArrayList<>();
        LocalDate today = LocalDate.now(clock.withZone(PartnerDocumentService.INDIA));
        PartnerDocumentEntity doc = subject.document();
        Map<String, Object> meta = doc == null ? Map.of() : metadata(doc);
        switch (subject.type()) {
            case IDENTITY -> {
                ApplicantProfile profile = subject.profile();
                if (profile == null || profile.dateOfBirth() == null) {
                    out.add(new Finding(CheckFinding.DATE_OF_BIRTH_MISSING, null));
                } else if (Period.between(profile.dateOfBirth(), today).getYears() < 18) {
                    out.add(new Finding(CheckFinding.UNDER_18, "Born " + PartnerDocumentService.plain(profile.dateOfBirth())));
                }
                photo(out, subject.record() == null ? null : subject.record().getIdPhotoWarnings(), "her ID");
            }
            case SELFIE_MATCH -> photo(out, subject.record() == null ? null : subject.record().getSelfieWarnings(), "her selfie");
            case LICENCE -> {
                expired(out, doc, today);
                name(out, meta, subject.profile());
                duplicates(out, subject, doc);
                previouslyRejected(out, doc);
                photo(out, text(meta.get(META_PHOTO_WARNINGS)), "the licence");
            }
            case VEHICLE -> {
                expired(out, doc, today);
                plate(out, doc, subject.profile());
                duplicates(out, subject, doc);
                previouslyRejected(out, doc);
                photo(out, text(meta.get(META_PHOTO_WARNINGS)), "the RC");
            }
            case INSURANCE -> {
                expired(out, doc, today);
                if (doc != null && InsuranceUseType.PRIVATE.name().equals(text(meta.get("insuranceUseType")))) {
                    out.add(new Finding(CheckFinding.NOT_COMMERCIAL, null));
                }
                photo(out, text(meta.get(META_PHOTO_WARNINGS)), "the policy");
            }
            case BACKGROUND -> photo(out, text(meta.get(META_PHOTO_WARNINGS)), "the certificate");
            case PAN -> {
                // Nothing to notice: optional, and its format is checked as she types it.
            }
        }
        return out;
    }

    private static void expired(List<Finding> out, PartnerDocumentEntity doc, LocalDate today) {
        if (doc != null && doc.getValidUntil() != null && doc.getValidUntil().isBefore(today)) {
            out.add(new Finding(CheckFinding.DOCUMENT_EXPIRED, "Expired on " + PartnerDocumentService.plain(doc.getValidUntil())));
        }
    }

    private static void name(List<Finding> out, Map<String, Object> meta, ApplicantProfile profile) {
        if (Boolean.TRUE.equals(meta.get(META_NAME_DECLARED))) {
            out.add(new Finding(CheckFinding.NAME_DECLARED, profile == null ? null : "Profile: " + profile.name()));
            return;
        }
        String typed = text(meta.get(META_NAME_ON_DOCUMENT));
        if (typed == null || profile == null || profile.name() == null) {
            return;
        }
        if (NameMatcher.compare(typed, profile.name()) == NameMatcher.Match.DIFFERENT) {
            out.add(new Finding(CheckFinding.NAME_MISMATCH, "Typed: " + typed + " · Profile: " + profile.name()));
        }
    }

    private static void plate(List<Finding> out, PartnerDocumentEntity doc, ApplicantProfile profile) {
        if (doc == null || doc.getDocumentNumber() == null || profile == null || profile.vehicleRegistrationNumber() == null) {
            return;
        }
        String onProfile = DocumentNumberRules.normalize(PartnerDocumentType.VEHICLE_RC, profile.vehicleRegistrationNumber());
        if (!onProfile.isEmpty() && !onProfile.equals(doc.getDocumentNumber())) {
            out.add(new Finding(CheckFinding.PLATE_MISMATCH, "RC: " + doc.getDocumentNumber() + " · Profile: " + onProfile));
        }
    }

    /** Other accounts with the same number on a document that was not turned down, and for a plate, on a profile. */
    private void duplicates(List<Finding> out, Subject subject, PartnerDocumentEntity doc) {
        if (doc == null || doc.getDocumentNumber() == null) {
            return;
        }
        Set<UUID> others = new LinkedHashSet<>();
        documents.findBySupersededAtIsNullAndTypeAndDocumentNumber(doc.getType(), doc.getDocumentNumber()).stream()
                .filter(d -> !d.getAccountId().equals(subject.accountId()))
                .filter(d -> d.getStatus() != PartnerDocumentStatus.REJECTED)
                .forEach(d -> others.add(d.getAccountId()));
        if (doc.getType() == PartnerDocumentType.VEHICLE_RC) {
            ApplicantProfiles source = profiles.getIfAvailable();
            if (source != null) {
                others.addAll(source.otherPartnersWithPlate(subject.accountId(), doc.getDocumentNumber()));
            }
        }
        if (!others.isEmpty()) {
            out.add(new Finding(CheckFinding.DUPLICATE_NUMBER,
                    "On " + others.size() + " other account" + (others.size() == 1 ? "" : "s")));
        }
    }

    private void previouslyRejected(List<Finding> out, PartnerDocumentEntity doc) {
        if (doc == null || doc.getDocumentNumber() == null) {
            return;
        }
        List<FlaggedDocumentNumberEntity> earlier = flagged.findByDocumentTypeAndDocumentNumber(doc.getType(), doc.getDocumentNumber())
                .stream().filter(f -> !doc.getId().equals(f.getDocumentId())).toList();
        if (!earlier.isEmpty()) {
            out.add(new Finding(CheckFinding.PREVIOUSLY_REJECTED, "Turned down before: " + String.join(", ",
                    earlier.stream().map(FlaggedDocumentNumberEntity::getReasonCode).distinct().toList())));
        }
    }

    private static void photo(List<Finding> out, String warnings, String what) {
        if (warnings != null && !warnings.isBlank()) {
            out.add(new Finding(CheckFinding.PHOTO_QUALITY, "Her phone said " + what + " may be " + PhotoWarnings.plain(warnings)));
        }
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    static Map<String, Object> metadata(PartnerDocumentEntity doc) {
        if (doc.getMetadataJson() == null || doc.getMetadataJson().isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(doc.getMetadataJson(), new TypeReference<Map<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return Map.of();
        }
    }
}
