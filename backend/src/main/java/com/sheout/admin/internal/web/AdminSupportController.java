package com.sheout.admin.internal.web;

import com.sheout.admin.internal.SupportOpsService;
import com.sheout.admin.internal.SupportTicketOpsDetail;
import com.sheout.admin.internal.SupportTicketOpsRow;
import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import com.sheout.support.SupportError;
import com.sheout.support.SupportTicketCategory;
import com.sheout.support.SupportTicketMessage;
import com.sheout.support.SupportTicketPriority;
import com.sheout.support.SupportTicketQuery;
import com.sheout.support.SupportTicketStatus;
import com.sheout.support.SupportTicketSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
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
 * The ops console's Support Tickets section. Every endpoint requires ADMIN -
 * a 403 role gate on the whole surface, the same reasoning AdminController
 * records for its own. A ticket id that does not exist is still a 404.
 * <p>
 * Status changes and assignment are confirmed in the console before they are
 * sent; the server does not rely on that, it enforces the transitions itself
 * (SupportTicketStatus.canMoveTo).
 */
@RestController
@RequestMapping("/api/v1/admin/support")
public class AdminSupportController {

    private final SupportOpsService supportOps;

    public AdminSupportController(SupportOpsService supportOps) {
        this.supportOps = supportOps;
    }

    /** The queue: unresolved first, HIGH first within that. Filters match the other paged lists. */
    @GetMapping("/tickets")
    public ResponseEntity<PageResponse<SupportTicketOpsRow>> tickets(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Set<SupportTicketStatus> status,
            @RequestParam(required = false) Set<SupportTicketCategory> category,
            @RequestParam(required = false) Set<SupportTicketPriority> priority,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        requireAdmin();
        SupportTicketQuery query = new SupportTicketQuery(null, status, category, priority, from, to);
        return ResponseEntity.ok(PageResponse.from(
                supportOps.tickets(query, PageRequest.of(
                        PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize))),
                row -> row));
    }

    /** For the sidebar badge and the SOS page's link to open safety tickets. */
    @GetMapping("/counts")
    public ResponseEntity<SupportOpsService.SupportCounts> counts() {
        requireAdmin();
        return ResponseEntity.ok(supportOps.counts());
    }

    @GetMapping("/operators")
    public ResponseEntity<List<SupportOpsService.Operator>> operators() {
        requireAdmin();
        return ResponseEntity.ok(supportOps.operators());
    }

    @GetMapping("/tickets/{ticketId}")
    public ResponseEntity<SupportTicketOpsDetail> ticket(@PathVariable UUID ticketId) {
        CurrentAccount admin = requireAdmin();
        return ResponseEntity.ok(supportOps.ticket(ticketId, admin.accountId())
                .orElseThrow(() -> ApiException.notFound("No such ticket")));
    }

    /** A reply the raiser sees (and is texted about), or with internalOnly, a note only operators see. */
    @PostMapping("/tickets/{ticketId}/messages")
    public ResponseEntity<SupportTicketMessage> message(@PathVariable UUID ticketId,
                                                        @Valid @RequestBody MessageRequest request) {
        CurrentAccount admin = requireAdmin();
        Result<SupportTicketMessage, SupportError> result = supportOps.addMessage(
                ticketId, admin.accountId(), request.message(), Boolean.TRUE.equals(request.internalOnly()));
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.value());
    }

    @PostMapping("/tickets/{ticketId}/status")
    public ResponseEntity<SupportTicketSummary> status(@PathVariable UUID ticketId,
                                                       @Valid @RequestBody StatusRequest request) {
        CurrentAccount admin = requireAdmin();
        return respond(supportOps.updateStatus(ticketId, request.status(), admin.accountId()));
    }

    /** assigneeAdminId null unassigns. */
    @PostMapping("/tickets/{ticketId}/assign")
    public ResponseEntity<SupportTicketSummary> assign(@PathVariable UUID ticketId,
                                                       @RequestBody AssignRequest request) {
        CurrentAccount admin = requireAdmin();
        return respond(supportOps.assign(ticketId, request.assigneeAdminId(), admin.accountId()));
    }

    private ResponseEntity<SupportTicketSummary> respond(Result<SupportTicketSummary, SupportError> result) {
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.ok(result.value());
    }

    private CurrentAccount requireAdmin() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.ADMIN) {
            throw ApiException.forbidden("Admin role required");
        }
        return caller;
    }

    private static ApiException toApiException(SupportError error) {
        return switch (error) {
            case TICKET_NOT_FOUND -> ApiException.notFound("No such ticket");
            // Not reachable from an operator's call today - only creation
            // checks a linked booking - but mapped rather than left to a 500.
            case BOOKING_NOT_FOUND -> ApiException.notFound("No booking found for this id");
            case TICKET_CLOSED -> new ApiException(HttpStatus.CONFLICT, "TICKET_CLOSED",
                    "This ticket is closed. A closed ticket cannot be changed or replied to; internal notes are still allowed.");
            case INVALID_STATUS_TRANSITION -> new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "That status change is not allowed from the ticket's current status.");
            case ASSIGNEE_NOT_ADMIN -> new ApiException(HttpStatus.BAD_REQUEST, "ASSIGNEE_NOT_ADMIN",
                    "Tickets can only be assigned to an operations account.");
        };
    }

    public record MessageRequest(@NotBlank @Size(max = 2000) String message, Boolean internalOnly) {
    }

    public record StatusRequest(@NotNull SupportTicketStatus status) {
    }

    public record AssignRequest(UUID assigneeAdminId) {
    }
}
