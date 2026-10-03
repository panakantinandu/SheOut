package com.sheout.staff.internal;

import com.sheout.sharedkernel.BaseEntity;
import com.sheout.staff.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One member of staff. See V61 for what each column is for.
 * <p>
 * Nothing in here is ever serialised to a response: the views in web/ pick
 * the fields that may leave, and the hash and secret are not among them.
 */
@Entity
@Table(name = "staff_members")
class StaffMemberEntity extends BaseEntity {

    /** A password hash nothing matches: set while a second-factor reset is pending. */
    static final String NO_PASSWORD = "!reset";

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StaffRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffStatus status;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "password_changed_at", nullable = false)
    private Instant passwordChangedAt;

    @Column(name = "totp_secret", nullable = false, length = 200)
    private String totpSecret;

    @Column(name = "totp_last_step")
    private Long totpLastStep;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "disabled_by")
    private UUID disabledBy;

    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    protected StaffMemberEntity() {
    }

    StaffMemberEntity(UUID accountId, String email, String displayName, StaffRole role, String passwordHash,
                      String encryptedTotpSecret, Long totpLastStep, Instant accessExpiresAt, UUID invitedBy) {
        this.accountId = accountId;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.status = StaffStatus.ACTIVE;
        this.passwordHash = passwordHash;
        this.passwordChangedAt = Instant.now();
        this.totpSecret = encryptedTotpSecret;
        this.totpLastStep = totpLastStep;
        this.accessExpiresAt = accessExpiresAt;
        this.invitedBy = invitedBy;
    }

    boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    boolean accessExpired(Instant now) {
        return accessExpiresAt != null && !accessExpiresAt.isAfter(now);
    }

    /** True when this failure locked the account. */
    boolean recordFailure(Instant now, int maxFailures, Duration lockFor) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockFor);
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    void recordSignIn(Instant now, long totpStep) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
        totpLastStep = totpStep;
    }

    /** A recovery code was used: the authenticator's replay guard is unaffected. */
    void recordSignInWithRecoveryCode(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    void acceptTotpStep(long step) {
        this.totpLastStep = step;
    }

    void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.passwordChangedAt = Instant.now();
    }

    /** Old password and authenticator stop working at once; the reset link sets new ones. */
    void clearCredentialsForReset() {
        this.passwordHash = NO_PASSWORD;
        this.totpSecret = NO_PASSWORD;
        this.totpLastStep = null;
    }

    void replaceCredentials(String newPasswordHash, String encryptedTotpSecret, long totpStep) {
        changePassword(newPasswordHash);
        this.totpSecret = encryptedTotpSecret;
        this.totpLastStep = totpStep;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    void disable(UUID by, String reason) {
        this.status = StaffStatus.DISABLED;
        this.disabledAt = Instant.now();
        this.disabledBy = by;
        this.disabledReason = reason;
    }

    void enable() {
        this.status = StaffStatus.ACTIVE;
        this.disabledAt = null;
        this.disabledBy = null;
        this.disabledReason = null;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    void changeRole(StaffRole role, Instant accessExpiresAt) {
        this.role = role;
        this.accessExpiresAt = role.requiresAccessExpiry() ? accessExpiresAt : null;
    }

    boolean hasSecondFactor() {
        return !NO_PASSWORD.equals(totpSecret);
    }

    UUID getAccountId() {
        return accountId;
    }

    String getEmail() {
        return email;
    }

    String getDisplayName() {
        return displayName;
    }

    StaffRole getRole() {
        return role;
    }

    StaffStatus getStatus() {
        return status;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    String getTotpSecret() {
        return totpSecret;
    }

    Long getTotpLastStep() {
        return totpLastStep;
    }

    Instant getLockedUntil() {
        return lockedUntil;
    }

    Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    Instant getLastLoginAt() {
        return lastLoginAt;
    }

    Instant getDisabledAt() {
        return disabledAt;
    }

    String getDisabledReason() {
        return disabledReason;
    }
}
