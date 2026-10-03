package com.sheout.admin.internal.web;

import com.sheout.admin.internal.DocumentQueueRow;
import com.sheout.admin.internal.PartnerVerificationOpsService;
import com.sheout.admin.internal.PartnerVerificationView;
import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Reads for the console's partner paperwork: her Documents tab and the four
 * documents queues. The decisions themselves - approve, reject, upload,
 * record a police check, open a file - are driver-verification's own admin
 * endpoints, called directly, as the ID check's always have been.
 */
@RestController
public class AdminPartnerDocumentsController {

    private final PartnerVerificationOpsService ops;

    public AdminPartnerDocumentsController(PartnerVerificationOpsService ops) {
        this.ops = ops;
    }

    @GetMapping("/api/v1/admin/partners/{accountId}/verification")
    public ResponseEntity<PartnerVerificationView> partner(@PathVariable UUID accountId) {
        requireAdmin();
        return ops.partner(accountId).map(ResponseEntity::ok)
                .orElseThrow(() -> ApiException.notFound("No verification record for this account"));
    }

    /** queue: TO_REVIEW, EXPIRING (30 days), EXPIRED, POLICE_DUE (30 days). */
    @GetMapping("/api/v1/admin/verification/document-queue")
    public ResponseEntity<List<DocumentQueueRow>> queue(@RequestParam PartnerVerificationOpsService.Queue queue) {
        requireAdmin();
        return ResponseEntity.ok(ops.documentQueue(queue));
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }
}
