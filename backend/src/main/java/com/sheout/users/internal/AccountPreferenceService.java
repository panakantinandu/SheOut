package com.sheout.users.internal;

import com.sheout.users.AccountLanguageApi;
import com.sheout.users.AppLanguage;
import com.sheout.users.FeatureWaitlistApi;
import com.sheout.users.WaitlistFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * An account's language, and its place on feature waitlists.
 * <p>
 * Every method takes the account id the controller read from the token -
 * there is no way to ask about anybody else.
 */
@Service
public class AccountPreferenceService implements FeatureWaitlistApi, AccountLanguageApi {

    private final AccountPreferenceRepository preferences;
    private final FeatureWaitlistRepository waitlist;

    AccountPreferenceService(AccountPreferenceRepository preferences, FeatureWaitlistRepository waitlist) {
        this.preferences = preferences;
        this.waitlist = waitlist;
    }

    /** Empty until she has chosen one; the app then goes by the phone's own language. */
    public Optional<AppLanguage> findLanguage(UUID accountId) {
        return preferences.findByAccountId(accountId).flatMap(p -> AppLanguage.fromCode(p.getLanguage()));
    }

    @Override
    public Optional<AppLanguage> languageOf(UUID accountId) {
        return findLanguage(accountId);
    }

    @Transactional
    public AppLanguage setLanguage(UUID accountId, AppLanguage language) {
        AccountPreferenceEntity row = preferences.findByAccountId(accountId)
                .orElseGet(() -> new AccountPreferenceEntity(accountId, language.code()));
        row.setLanguage(language.code());
        preferences.save(row);
        return language;
    }

    /** When she joined, if she has. */
    public Optional<Instant> joinedAt(UUID accountId, WaitlistFeature feature) {
        return waitlist.findByAccountIdAndFeature(accountId, feature).map(FeatureWaitlistEntity::getCreatedAt);
    }

    /** Idempotent: a second tap changes nothing and still answers with the original time. */
    @Transactional
    public Instant join(UUID accountId, WaitlistFeature feature) {
        waitlist.joinIfAbsent(accountId, feature.name());
        return joinedAt(accountId, feature).orElseThrow();
    }

    @Override
    public long countInterested(WaitlistFeature feature) {
        return waitlist.countByFeature(feature);
    }

    /** Called when the account is deleted - neither record is worth keeping. */
    @Transactional
    void forget(UUID accountId) {
        preferences.deleteByAccountId(accountId);
        waitlist.deleteByAccountId(accountId);
    }
}
