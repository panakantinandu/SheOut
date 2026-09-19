package com.sheout.users.internal;

import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** One account's app preferences. Today only the language. */
@Entity
@Table(name = "account_preferences")
public class AccountPreferenceEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID accountId;

    /** The code the apps use - en, te or hi. See AppLanguage. */
    @Column(nullable = false, length = 5)
    private String language;

    protected AccountPreferenceEntity() {
        // JPA
    }

    AccountPreferenceEntity(UUID accountId, String language) {
        this.accountId = accountId;
        this.language = language;
    }

    UUID getAccountId() {
        return accountId;
    }

    String getLanguage() {
        return language;
    }

    void setLanguage(String language) {
        this.language = language;
    }
}
