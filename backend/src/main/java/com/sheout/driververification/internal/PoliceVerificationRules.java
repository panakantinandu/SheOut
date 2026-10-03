package com.sheout.driververification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * The configurable parts of police verification, in one place.
 * <p>
 * reverifyMonths: how long a police check stands before it must be redone.
 * Twelve is an assumption, not a rule anybody has given us - it is one of
 * the founder's open questions (README).
 * <p>
 * acceptThirdPartyBgvAlone: false until a lawyer says a private background
 * check by itself satisfies "police verification" in Telangana. While false
 * a background report can be attached beside a police certificate but
 * cannot make the check VERIFIED.
 * <p>
 * consentVersion: the version of the verification consent the apps show
 * (design-system legal content, VERIFICATION_CONSENT_VERSION). Changing the
 * wording means bumping both, and every partner is asked again.
 */
@Component
public class PoliceVerificationRules {

    private static final Logger log = LoggerFactory.getLogger(PoliceVerificationRules.class);

    private final int reverifyMonths;
    private final boolean acceptThirdPartyBgvAlone;
    private final List<Integer> reminderDays;
    private final String consentVersion;

    public PoliceVerificationRules(
            @Value("${POLICE_REVERIFY_MONTHS:12}") int reverifyMonths,
            @Value("${POLICE_ACCEPT_THIRD_PARTY_BGV_ALONE:false}") boolean acceptThirdPartyBgvAlone,
            @Value("${POLICE_REVERIFY_REMINDER_DAYS:30,7}") String reminderDays,
            @Value("${VERIFICATION_CONSENT_VERSION:2026-10-03-draft}") String consentVersion) {
        if (reverifyMonths < 1) {
            throw new IllegalArgumentException("POLICE_REVERIFY_MONTHS must be at least 1, got " + reverifyMonths);
        }
        if (consentVersion == null || consentVersion.isBlank() || consentVersion.length() > 40) {
            throw new IllegalArgumentException("VERIFICATION_CONSENT_VERSION must be set, at most 40 characters");
        }
        this.reverifyMonths = reverifyMonths;
        this.acceptThirdPartyBgvAlone = acceptThirdPartyBgvAlone;
        this.reminderDays = Arrays.stream(reminderDays.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Integer::valueOf)
                .filter(d -> d > 0).distinct().sorted((a, b) -> b - a).toList();
        this.consentVersion = consentVersion.trim();
        log.info("Police checks stand {} months; a background check alone is {}accepted; consent version {}",
                reverifyMonths, acceptThirdPartyBgvAlone ? "" : "NOT ", this.consentVersion);
    }

    public int reverifyMonths() {
        return reverifyMonths;
    }

    public boolean acceptThirdPartyBgvAlone() {
        return acceptThirdPartyBgvAlone;
    }

    /** Largest first: 30, 7. */
    public List<Integer> reminderDays() {
        return reminderDays;
    }

    public String consentVersion() {
        return consentVersion;
    }
}
