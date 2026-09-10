package com.sheout.notifications.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.internal.sos.SosService;
import com.sheout.sharedkernel.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ASSUMPTION FLAGGED on HTTP status: POST always returns 200 once the alert
 * itself is recorded, even when zero contacts exist or every send failed -
 * the response body's {@code success} flag (see SosService.SosOutcome) is
 * the source of truth the frontend checks, rather than this endpoint
 * overloading a non-2xx status to mean "partially or fully failed to
 * deliver, but the request itself was fine." Not specified either way by
 * the spec; chosen because customer-app's generic API client (see
 * api/client.ts request()) throws on any non-2xx and unwraps the body as a
 * generic ApiErrorResponse, which isn't this response's actual shape - a
 * single always-2xx-with-a-truthful-body contract is simpler for a
 * safety-critical screen to handle correctly than asking it to special-case
 * two different non-2xx statuses AND parse two different body shapes.
 * <p>
 * GET /active is gated to ADMIN, same as AdminVerificationController - see
 * that controller's Javadoc for why there is no real way to provision an
 * ADMIN account yet (the admin module doesn't exist).
 */
@RestController
@RequestMapping("/api/v1/notifications/sos")
public class SosController {

    private final SosService sosService;

    public SosController(SosService sosService) {
        this.sosService = sosService;
    }

    @PostMapping
    public ResponseEntity<SosResponse> trigger(@Valid @RequestBody SosRequest request) {
        CurrentAccount caller = requireCustomer();
        SosService.SosOutcome outcome = sosService.trigger(caller.accountId(), request.lat(), request.lng(), request.bookingId());
        return ResponseEntity.ok(SosResponse.from(outcome));
    }

    @GetMapping("/active")
    public ResponseEntity<List<SosAlertSummary>> active() {
        requireAdmin();
        return ResponseEntity.ok(sosService.findActiveAlerts());
    }

    private CurrentAccount requireCustomer() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER) {
            throw ApiException.forbidden("Customer role required");
        }
        return caller;
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }

    public record SosRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            UUID bookingId
    ) {
    }

    public record SosResponse(
            UUID alertId,
            int contactsTotal,
            int contactsNotified,
            int contactsFailed,
            List<SosService.SosOutcome.ContactOutcome> contacts,
            boolean success,
            String reason
    ) {
        static SosResponse from(SosService.SosOutcome outcome) {
            String reason = outcome.contactsTotal() == 0
                    ? "NO_EMERGENCY_CONTACTS"
                    : outcome.success() ? null : "ALL_SENDS_FAILED";
            return new SosResponse(
                    outcome.alertId(),
                    outcome.contactsTotal(),
                    outcome.contactsNotified(),
                    outcome.contactsTotal() - outcome.contactsNotified(),
                    outcome.contacts(),
                    outcome.success(),
                    reason
            );
        }
    }

}
