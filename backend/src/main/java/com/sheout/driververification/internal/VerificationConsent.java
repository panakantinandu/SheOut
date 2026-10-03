package com.sheout.driververification.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.Result;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * A partner's agreement to SheOut verifying her identity, documents and
 * police or criminal record - asked before she uploads anything, versioned,
 * and asked again when the wording changes.
 * <p>
 * Partners only. A rider's ID check is covered by the privacy policy she
 * accepted at signup; a partner's check reaches her criminal record, which
 * is a different order of thing to ask, and so is asked for on its own.
 * <p>
 * Its own component because both the document intake and the police
 * decision need it, and the police decision already depends on the intake.
 */
@Component
public class VerificationConsent {

    private final VerificationRecordRepository records;
    private final PoliceVerificationRules rules;
    private final VerificationAudit audit;

    VerificationConsent(VerificationRecordRepository records, PoliceVerificationRules rules, VerificationAudit audit) {
        this.records = records;
        this.rules = rules;
        this.audit = audit;
    }

    public String currentVersion() {
        return rules.consentVersion();
    }

    /** True for a rider (nothing to ask) and for a partner who agreed to the current wording. */
    public boolean isCurrent(UUID accountId) {
        return records.findByAccountId(accountId).map(this::isCurrent).orElse(false);
    }

    boolean isCurrent(VerificationRecordEntity record) {
        return record.getRole() != AccountRole.DRIVER || rules.consentVersion().equals(record.getConsentVersion());
    }

    /**
     * Records that she agreed, to the version she was shown. A version other
     * than the current one is refused: she read different words from the ones
     * in force, and agreeing to those is not agreeing to these.
     */
    @Transactional
    public Result<VerificationRecordEntity, VerificationError> accept(UUID accountId, String version) {
        VerificationRecordEntity record = records.findByAccountId(accountId).orElse(null);
        if (record == null) {
            return Result.failure(VerificationError.RECORD_NOT_FOUND);
        }
        if (version == null || !rules.consentVersion().equals(version.trim())) {
            return Result.failure(VerificationError.CONSENT_VERSION_OUTDATED);
        }
        record.recordConsent(rules.consentVersion(), Instant.now());
        records.save(record);
        audit.record(accountId, accountId, VerificationAudit.ACTOR_PARTNER, VerificationAudit.CONSENT_ACCEPTED, null, null,
                "Version " + rules.consentVersion());
        return Result.success(record);
    }
}
