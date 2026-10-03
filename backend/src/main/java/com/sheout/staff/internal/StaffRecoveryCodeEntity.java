package com.sheout.staff.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One single-use recovery code. Only its HMAC is kept (see StaffSecrets). */
@Entity
@Table(name = "staff_recovery_codes")
class StaffRecoveryCodeEntity {

    @Id
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected StaffRecoveryCodeEntity() {
    }

    StaffRecoveryCodeEntity(UUID staffId, String codeHash) {
        this.id = UUID.randomUUID();
        this.staffId = staffId;
        this.codeHash = codeHash;
        this.createdAt = Instant.now();
    }

    void markUsed() {
        this.usedAt = Instant.now();
    }

    boolean isUsed() {
        return usedAt != null;
    }

    String getCodeHash() {
        return codeHash;
    }
}
