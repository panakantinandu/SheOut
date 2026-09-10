package com.sheout.admin.internal.web;

import com.sheout.admin.internal.AdminService;
import com.sheout.admin.internal.BookingOpsRow;
import com.sheout.admin.internal.ReviewQueueRow;
import com.sheout.admin.internal.SosAlertRow;
import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosError;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Operator console API. Every endpoint requires ADMIN.
 * <p>
 * These are 403s, not 404s, and that is the deliberate opposite of what
 * BookingController/PaymentController do for per-resource checks. The rule
 * this codebase follows is about not leaking whether a specific id exists:
 * a non-participant asking about one booking gets 404 so it cannot be told
 * apart from a booking that never existed. requireAdmin here is a pure role
 * gate on a whole surface - it runs before any id is looked at, and refusing
 * it reveals nothing about any particular resource, exactly like
 * BookingController's retained "DRIVER role required" 403.
 * <p>
 * Review approve/reject is NOT proxied here. driver-verification already
 * owns that transition on AdminVerificationController
 * (/api/v1/admin/verification/{id}/gender-review and /police-review); the
 * console calls those directly. Wrapping them would give one state change
 * two entry points to keep in step for no gain.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final int MAX_RECENT_BOOKINGS = 200;

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/verification/review-queue")
    public ResponseEntity<List<ReviewQueueRow>> reviewQueue() {
        requireAdmin();
        return ResponseEntity.ok(adminService.reviewQueue());
    }

    /**
     * The document itself is not proxied through this response - this hands
     * back the storage-resolved URL for the reviewer to open, keeping the
     * bytes on whatever storage backend driver-verification chose (local
     * disk or S3) instead of streaming them through the API.
     */
    @GetMapping("/verification/{accountId}/document")
    public ResponseEntity<DocumentResponse> document(@PathVariable UUID accountId) {
        requireAdmin();
        return adminService.documentUrl(accountId)
                .map(url -> ResponseEntity.ok(new DocumentResponse(accountId, url)))
                .orElseThrow(() -> ApiException.notFound("No document submitted for this account"));
    }

    @GetMapping("/sos/active")
    public ResponseEntity<List<SosAlertRow>> activeAlerts() {
        requireAdmin();
        return ResponseEntity.ok(adminService.activeAlerts());
    }

    @PostMapping("/sos/{alertId}/resolve")
    public ResponseEntity<SosAlertSummary> resolveAlert(@PathVariable UUID alertId) {
        CurrentAccount admin = requireAdmin();
        Result<SosAlertSummary, SosError> result = adminService.resolveAlert(alertId, admin.accountId());
        if (result.isFailure()) {
            throw switch (result.error()) {
                case ALERT_NOT_FOUND -> ApiException.notFound("No such SOS alert");
                case ALREADY_RESOLVED ->
                        new ApiException(HttpStatus.CONFLICT, "Conflict", "This alert is already resolved");
            };
        }
        return ResponseEntity.ok(result.value());
    }

    @GetMapping("/bookings/recent")
    public ResponseEntity<List<BookingOpsRow>> recentBookings(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        requireAdmin();
        int capped = Math.clamp(limit, 1, MAX_RECENT_BOOKINGS);
        return ResponseEntity.ok(adminService.recentBookings(capped));
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }

    public record DocumentResponse(UUID accountId, String url) {
    }
}
