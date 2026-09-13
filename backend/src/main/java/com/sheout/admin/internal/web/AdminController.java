package com.sheout.admin.internal.web;

import com.sheout.admin.internal.AccountOpsRow;
import com.sheout.admin.internal.AdminService;
import com.sheout.admin.internal.BookingOpsRow;
import com.sheout.admin.internal.TrustReviewRow;
import com.sheout.admin.internal.ReviewQueueRow;
import com.sheout.admin.internal.SosAlertRow;
import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.booking.BookingCategory;
import com.sheout.booking.BookingQuery;
import com.sheout.booking.BookingStatus;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosError;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
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
     * Both documents for one review, in one response.
     * <p>
     * Neither image is proxied through here - this hands back
     * storage-resolved URLs for the reviewer to open, keeping the bytes on
     * whatever storage backend is configured (local disk or S3) instead of
     * streaming them through the API.
     * <p>
     * One call rather than two, because an operator reads them together:
     * the identity document to establish who she is, and the registration
     * certificate to check the number she typed matches the vehicle she
     * actually owns. Two round trips would let the screen render half a
     * decision's evidence and look complete.
     * <p>
     * rcUrl is null for every rider, who has no vehicle, and for partners
     * whose submission predates the requirement. The console says which of
     * those it is rather than showing a gap.
     */
    @GetMapping("/verification/{accountId}/document")
    public ResponseEntity<DocumentResponse> document(@PathVariable UUID accountId) {
        requireAdmin();
        return adminService.documentUrl(accountId)
                .map(url -> ResponseEntity.ok(
                        new DocumentResponse(accountId, url, adminService.rcDocumentUrl(accountId).orElse(null))))
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

    /**
     * The bookings table, paged and filtered. Replaces what /bookings/recent
     * gave the console: a hard cap of 50 rows with no way to reach the
     * fifty-first and no way to narrow them. That endpoint stays for now
     * because the console's own rollout is a separate deploy from this one.
     */
    @GetMapping("/bookings")
    public ResponseEntity<PageResponse<BookingOpsRow>> bookings(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Set<BookingStatus> status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Set<BookingCategory> category,
            @RequestParam(required = false) String q) {
        requireAdmin();
        Pageable pageable = PageRequest.of(
                PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize));
        return ResponseEntity.ok(PageResponse.from(
                adminService.pagedBookings(new BookingQuery(status, from, to, category, q), pageable),
                row -> row));
    }

    /**
     * The account list. blocked is three-state: omit it for every account,
     * true for only blocked, false for only active.
     */
    @GetMapping("/accounts")
    public ResponseEntity<PageResponse<AccountOpsRow>> accounts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AccountRole role,
            @RequestParam(required = false) Boolean blocked) {
        requireAdmin();
        Pageable pageable = PageRequest.of(
                PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize));
        return ResponseEntity.ok(PageResponse.from(
                adminService.accounts(q, role, blocked, pageable), row -> row));
    }

    /**
     * Blocks an account, with a reason that is genuinely required.
     * <p>
     * A blank reason is a 400, not a stored empty string. An audit row that
     * says nothing is worse than no audit row, because it looks answered:
     * whoever asks later why this person lost access finds a record that
     * confirms it happened and explains nothing.
     * <p>
     * An admin cannot block an admin, or themselves, from here. That is not
     * a guardrail against malice - an operator with this endpoint has other
     * options - it is a guardrail against the single misclick that locks
     * the operations team out of its own console.
     */
    @PostMapping("/accounts/{accountId}/block")
    public ResponseEntity<AccountOpsRow> block(
            @PathVariable UUID accountId,
            @Valid @RequestBody BlockRequest request) {
        CurrentAccount caller = requireAdmin();
        if (accountId.equals(caller.accountId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "You cannot block your own account");
        }
        AccountOpsRow target = adminService.findAccountRow(accountId)
                .orElseThrow(() -> ApiException.notFound("No such account"));
        if (target.role() == AccountRole.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request",
                    "Operations accounts cannot be blocked from the console");
        }
        return ResponseEntity.ok(adminService.block(accountId, caller.accountId(), request.reason().trim())
                .orElseThrow(() -> ApiException.notFound("No such account")));
    }

    @PostMapping("/accounts/{accountId}/unblock")
    public ResponseEntity<AccountOpsRow> unblock(@PathVariable UUID accountId) {
        requireAdmin();
        return ResponseEntity.ok(adminService.unblock(accountId)
                .orElseThrow(() -> ApiException.notFound("No such account")));
    }

    /**
     * The trust review queue: every account flagged by any signal, in one
     * list.
     * <p>
     * Sits beside the verification review queue rather than inside the
     * accounts table, because it is the same kind of screen: a list of
     * accounts waiting on a human decision, worked through and emptied.
     * <p>
     * One queue rather than one per signal. Cancelling too often and being
     * rated badly both raise the same flag, and an account doing both
     * appears once with both facts on the row - which is what lets an
     * operator finish the list and know it is finished.
     * <p>
     * Being flagged puts an account here and does nothing else - no block,
     * no throttle, no fee. Blocking remains the separate, deliberate action
     * next door, taken by an operator who can see the figures this row
     * carries.
     */
    @GetMapping("/trust/review-queue")
    public ResponseEntity<List<TrustReviewRow>> trustReviewQueue() {
        requireAdmin();
        return ResponseEntity.ok(adminService.trustReviewQueue());
    }

    /**
     * Takes an account off the review queue once an operator has looked.
     * <p>
     * Does not reset any counter or average. They are facts about what
     * happened, and an operator's decision does not change what happened -
     * clearing only records that somebody has read them. An account that
     * keeps cancelling, or keeps being rated badly, crosses the line again
     * and comes back, which is what a review queue should do.
     */
    @PostMapping("/accounts/{accountId}/clear-review-flag")
    public ResponseEntity<AccountOpsRow> clearReviewFlag(@PathVariable UUID accountId) {
        requireAdmin();
        AccountOpsRow target = adminService.findAccountRow(accountId)
                .orElseThrow(() -> ApiException.notFound("No such account"));
        adminService.clearReviewFlag(accountId, target.role());
        return ResponseEntity.ok(adminService.findAccountRow(accountId)
                .orElseThrow(() -> ApiException.notFound("No such account")));
    }

    public record BlockRequest(@NotBlank @Size(max = 500) String reason) {
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }

    /** rcUrl is null for riders and for partners who submitted before the RC was required. */
    public record DocumentResponse(UUID accountId, String url, String rcUrl) {
    }
}
