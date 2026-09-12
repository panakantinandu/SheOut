package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

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

    /**
     * Non-null means blocked. The same shape SosAlertEntity uses for
     * resolution: the timestamp is the flag, and who did it and why sit
     * beside it so the decision is answerable later. See V8.
     */
    @Column
    private Instant blockedAt;

    @Column
    private UUID blockedBy;

    @Column(length = 500)
    private String blockReason;

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

    /**
     * Only AuthService.grantAdminRole calls this. A role is otherwise fixed
     * for the life of an account (one phone number, one role - see this
     * class's Javadoc), so this is deliberately not a general setter.
     */
    void promoteToAdmin() {
        this.role = AccountRole.ADMIN;
    }

    public boolean isBlocked() {
        return blockedAt != null;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public UUID getBlockedBy() {
        return blockedBy;
    }

    public String getBlockReason() {
        return blockReason;
    }

    /** A reason is required by the caller, not defaulted here - see AdminAccountService. */
    void block(UUID adminAccountId, String reason) {
        this.blockedAt = Instant.now();
        this.blockedBy = adminAccountId;
        this.blockReason = reason;
    }

    /**
     * Clears all three, rather than keeping the old reason beside a null
     * timestamp. A stale reason on an active account is a trap: it reads as
     * current in every list and every detail view that shows it, and there
     * is no field saying it is historical. If a block history is wanted it
     * needs its own table, which is a bigger thing than this.
     */
    void unblock() {
        this.blockedAt = null;
        this.blockedBy = null;
        this.blockReason = null;
    }
}
