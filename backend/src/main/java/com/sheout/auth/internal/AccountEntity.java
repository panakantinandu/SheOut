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
     * When she accepted the Terms and Privacy Policy, and which version she
     * saw. Null for accounts created before consent was recorded - which is
     * "no record", not a refusal.
     */
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    @Column(name = "terms_version", length = 40)
    private String termsVersion;

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

    /** Set once when the account holder deletes the account; see markDeleted. */
    @Column
    private Instant deletedAt;

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
     * for the life of an account - a number holds one account per app, each
     * with its own role (see V26) - so this is deliberately not a general
     * setter.
     */
    void promoteToAdmin() {
        this.role = AccountRole.ADMIN;
    }

    /** Idempotent: the first acceptance is the one that counts, and stands. */
    void acceptTerms(String version) {
        if (termsAcceptedAt != null) {
            return;
        }
        this.termsAcceptedAt = Instant.now();
        this.termsVersion = version;
    }

    boolean hasAcceptedTerms() {
        return termsAcceptedAt != null;
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * The account holder's deletion. The row stays - bookings and payments
     * that are retained for tax and disputes reference this id - but the
     * person is unlinked from it: phone number and email are removed, not
     * hashed or kept aside.
     * <p>
     * No reference to the old number is retained. Nothing in the product
     * uses one today, and a hash of a ten-digit Indian mobile number is
     * reversible by trying every number, so keeping one "non-reversibly"
     * would be a claim rather than a fact. If fraud work later needs to
     * recognise a returning number, that is a decision to make then, with a
     * keyed hash and its own justification.
     * <p>
     * The block reason is cleared too: it is free text an operator wrote
     * about this person.
     */
    void markDeleted() {
        this.deletedAt = Instant.now();
        this.phoneNumber = null;
        this.email = null;
        this.blockReason = null;
    }
}
