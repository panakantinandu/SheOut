package com.sheout.staff.internal;

import com.sheout.staff.Approvals;
import com.sheout.staff.RequiresStepUp;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffDirectory;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffSignedIn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Approvals page: what is waiting for this person, what she asked for,
 * and what was recently decided. Deciding needs the request's approving
 * permission (checked inside) and a fresh authenticator code.
 */
@RestController
@RequestMapping("/api/v1/admin/approvals")
@StaffSignedIn
class ApprovalController {

    private final ApprovalService approvals;
    private final StaffDirectory directory;
    private final StaffManagementService management;

    ApprovalController(ApprovalService approvals, StaffDirectory directory, StaffManagementService management) {
        this.approvals = approvals;
        this.directory = directory;
        this.management = management;
    }

    record View(UUID id, Approvals.Kind kind, String status, String summary, String reason, String requestedBy,
                Instant requestedAt, Instant expiresAt, String decidedBy, Instant decidedAt, String decisionNote,
                boolean selfApproved, String result, String beforeJson, String payload, boolean yours,
                String downloadPath) {
    }

    record Page(List<View> waiting, List<View> mine, List<View> recent, boolean soleOwner) {
    }

    @GetMapping
    ResponseEntity<Page> list() {
        StaffPrincipal me = StaffContext.requireSignedIn();
        if (me.legacy()) {
            return ResponseEntity.ok(new Page(List.of(), List.of(), List.of(), false));
        }
        ApprovalService.Lists lists = approvals.lists(me);
        return ResponseEntity.ok(new Page(views(lists.waiting(), me), views(lists.mine(), me), views(lists.recent(), me),
                approvals.soleOwner(me)));
    }

    record DecisionRequest(@Size(max = 500) String note) {
    }

    @RequiresStepUp
    @PostMapping("/{id}/approve")
    ResponseEntity<ApprovalService.Decision> approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest body) {
        return ResponseEntity.ok(approvals.decide(id, true, body == null ? null : body.note()));
    }

    @PostMapping("/{id}/reject")
    ResponseEntity<ApprovalService.Decision> reject(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest body) {
        return ResponseEntity.ok(approvals.decide(id, false, body == null ? null : body.note()));
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable UUID id) {
        approvals.cancel(id);
        return ResponseEntity.noContent().build();
    }

    record ExportRequest(@NotBlank @Size(max = 1000) String path, @Size(max = 500) String reason) {
    }

    /** Asks to download something an @Export endpoint serves; approved once, used once. */
    @PostMapping("/exports")
    ResponseEntity<Approvals.Submitted> askForExport(@Valid @RequestBody ExportRequest body) {
        return ResponseEntity.accepted().body(approvals.submitExport(body.path(), body.reason()));
    }

    private List<View> views(List<ApprovalEntity> rows, StaffPrincipal me) {
        return rows.stream().map(a -> {
            boolean yours = a.getRequestedByStaffId().equals(me.staffId());
            String download = yours && a.getKind() == Approvals.Kind.EXPORT && a.getStatus() == ApprovalEntity.Status.APPROVED
                    ? a.getExportPath() + (a.getExportPath().contains("?") ? "&" : "?") + "approval=" + a.getId() : null;
            return new View(a.getId(), a.getKind(), a.getStatus().name(), a.getSummary(), a.getReason(),
                    directory.label(a.getRequestedByAccountId()).orElse("Former staff"), a.getCreatedAt(), a.getExpiresAt(),
                    a.getDecidedByStaffId() == null ? null
                            : management.find(a.getDecidedByStaffId()).map(m -> m.getDisplayName()).orElse("Former staff"),
                    a.getDecidedAt(), a.getDecisionNote(), a.isSelfApproved(), a.getResultMessage(), a.getBeforeJson(),
                    a.getPayload(), yours, download);
        }).toList();
    }
}
