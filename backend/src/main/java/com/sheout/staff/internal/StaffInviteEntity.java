package com.sheout.staff.internal;

import com.sheout.staff.StaffRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation to join the staff, or (staffMemberId set) a link to set a new
 * password and authenticator after an owner reset them. See V61.
 * <p>
 * Usable once and only until expiresAt. Choosing a password parks it here
 * (pendingPasswordHash) until the authenticator is confirmed; only then does
 * a staff member exist, so an invitation abandoned half way leaves nothing
 * that can sign in.
 */
@Entity
@Table(name = "staff_invites")
class StaffInviteEntity {

    enum Status { PENDING, USED, REVOKED }

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StaffRole role;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "staff_member_id")
    private UUID staffMemberId;

    @Column(name = "link_account_id")
    private UUID linkAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "pending_password_hash", length = 200)
    private String pendingPasswordHash;

    @Column(name = "pending_totp_secret", length = 200)
    private String pendingTotpSecret;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected StaffInviteEntity() {
    }

    StaffInviteEntity(String tokenHash, String email, String displayName, StaffRole role, Instant accessExpiresAt,
                      UUID invitedBy, UUID staffMemberId, UUID linkAccountId, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.tokenHash = tokenHash;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.accessExpiresAt = accessExpiresAt;
        this.invitedBy = invitedBy;
        this.staffMemberId = staffMemberId;
        this.linkAccountId = linkAccountId;
        this.status = Status.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    boolean usable(Instant now) {
        return status == Status.PENDING && expiresAt.isAfter(now);
    }

    void park(String passwordHash, String encryptedTotpSecret) {
        this.pendingPasswordHash = passwordHash;
        this.pendingTotpSecret = encryptedTotpSecret;
    }

    void markUsed() {
        this.status = Status.USED;
        this.usedAt = Instant.now();
        this.pendingPasswordHash = null;
        this.pendingTotpSecret = null;
    }

    void revoke() {
        if (status != Status.PENDING) {
            return;
        }
        this.status = Status.REVOKED;
        this.revokedAt = Instant.now();
        this.pendingPasswordHash = null;
        this.pendingTotpSecret = null;
    }

    boolean isReset() {
        return staffMemberId != null;
    }

    UUID getId() {
        return id;
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

    Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    UUID getInvitedBy() {
        return invitedBy;
    }

    UUID getStaffMemberId() {
        return staffMemberId;
    }

    UUID getLinkAccountId() {
        return linkAccountId;
    }

    Status getStatus() {
        return status;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    String getPendingPasswordHash() {
        return pendingPasswordHash;
    }

    String getPendingTotpSecret() {
        return pendingTotpSecret;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
