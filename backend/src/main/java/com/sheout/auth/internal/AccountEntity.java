package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class AccountEntity extends BaseEntity {

    /** Null for a Google-created account - see V4__google_signin migration. */
    @Column(unique = true, length = 20)
    private String phoneNumber;

    /** Null for a phone-created account. Always stored lowercased - see AuthService. */
    @Column(unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    protected AccountEntity() {
        // JPA
    }

    public AccountEntity(String phoneNumber, AccountRole role) {
        this.phoneNumber = phoneNumber;
        this.role = role;
    }

    /**
     * Google sign-in has no phone number at creation time - phoneNumber
     * stays null unless a future "link your phone" flow sets it (not built;
     * see AuthService's Javadoc on account linking).
     */
    public static AccountEntity forGoogleSignIn(String email, AccountRole role) {
        AccountEntity entity = new AccountEntity();
        entity.email = email;
        entity.role = role;
        return entity;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public AccountRole getRole() {
        return role;
    }
}
