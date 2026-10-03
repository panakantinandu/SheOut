package com.sheout.staff.internal;

import com.sheout.staff.Approvals;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One four-eyes request. See V63 and staff.Approvals. */
@Entity
@Table(name = "staff_approval_requests")
class ApprovalEntity {

    enum Status { PENDING, APPROVED, EXECUTED, FAILED, REJECTED, CANCELLED, EXPIRED }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Approvals.Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, length = 8000)
    private String payload;

    @Column(name = "before_json", length = 8000)
    private String beforeJson;

    @Column(name = "approver_permission", nullable = false, length = 60)
    private String approverPermission;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id", length = 80)
    private String targetId;

    @Column(name = "requested_by_staff_id", nullable = false)
    private UUID requestedByStaffId;

    @Column(name = "requested_by_account_id", nullable = false)
    private UUID requestedByAccountId;

    @Column(name = "requested_by_role", nullable = false, length = 30)
    private String requestedByRole;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_by_staff_id")
    private UUID decidedByStaffId;

    @Column(name = "decided_by_account_id")
    private UUID decidedByAccountId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "self_approved", nullable = false)
    private boolean selfApproved;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "result_message", length = 1000)
    private String resultMessage;

    @Column(name = "export_path", length = 1000)
    private String exportPath;

    @Column(name = "export_used_at")
    private Instant exportUsedAt;

    protected ApprovalEntity() {
    }

    ApprovalEntity(Approvals.Request request, UUID staffId, UUID accountId, String role, Instant expiresAt, String exportPath) {
        this.id = UUID.randomUUID();
        this.kind = request.kind();
        this.status = Status.PENDING;
        this.summary = cut(request.summary(), 500);
        this.payload = request.payload() == null ? "{}" : request.payload();
        this.beforeJson = request.beforeJson();
        this.approverPermission = request.approverPermission().key();
        this.targetType = request.targetType();
        this.targetId = request.targetId();
        this.reason = cut(request.reason(), 500);
        this.requestedByStaffId = staffId;
        this.requestedByAccountId = accountId;
        this.requestedByRole = role;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.exportPath = exportPath;
    }

    boolean expireIfDue(Instant now) {
        if (status == Status.PENDING && !expiresAt.isAfter(now)) {
            status = Status.EXPIRED;
            return true;
        }
        return false;
    }

    void decide(boolean approved, UUID staffId, UUID accountId, String note, boolean self) {
        this.status = approved ? Status.APPROVED : Status.REJECTED;
        this.decidedByStaffId = staffId;
        this.decidedByAccountId = accountId;
        this.decidedAt = Instant.now();
        this.decisionNote = cut(note, 500);
        this.selfApproved = self;
    }

    void cancel() {
        this.status = Status.CANCELLED;
        this.decidedAt = Instant.now();
    }

    void executed(boolean ok, String message) {
        this.status = ok ? Status.EXECUTED : Status.FAILED;
        this.executedAt = Instant.now();
        this.resultMessage = cut(message, 1000);
    }

    void exportUsed() {
        this.exportUsedAt = Instant.now();
        this.status = Status.EXECUTED;
        this.executedAt = this.exportUsedAt;
        this.resultMessage = "Downloaded";
    }

    private static String cut(String v, int max) {
        return v == null ? null : v.length() <= max ? v : v.substring(0, max);
    }

    UUID getId() { return id; }
    Approvals.Kind getKind() { return kind; }
    Status getStatus() { return status; }
    String getSummary() { return summary; }
    String getPayload() { return payload; }
    String getBeforeJson() { return beforeJson; }
    String getApproverPermission() { return approverPermission; }
    String getTargetType() { return targetType; }
    String getTargetId() { return targetId; }
    UUID getRequestedByStaffId() { return requestedByStaffId; }
    UUID getRequestedByAccountId() { return requestedByAccountId; }
    String getRequestedByRole() { return requestedByRole; }
    String getReason() { return reason; }
    Instant getCreatedAt() { return createdAt; }
    Instant getExpiresAt() { return expiresAt; }
    UUID getDecidedByStaffId() { return decidedByStaffId; }
    Instant getDecidedAt() { return decidedAt; }
    String getDecisionNote() { return decisionNote; }
    boolean isSelfApproved() { return selfApproved; }
    Instant getExecutedAt() { return executedAt; }
    String getResultMessage() { return resultMessage; }
    String getExportPath() { return exportPath; }
    Instant getExportUsedAt() { return exportUsedAt; }
}
