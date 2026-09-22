package com.sheout.support.internal.web;


import com.sheout.sharedkernel.ratelimit.RateLimiter;import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.PageResponse;
import com.sheout.support.CreateTicketCommand;
import com.sheout.support.SupportApi;
import com.sheout.support.SupportError;
import com.sheout.support.SupportTicketCategory;
import com.sheout.support.SupportTicketDetail;
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
 * A rider's or a partner's own tickets. Who is asking always comes from the
 * token; there is no parameter anywhere here naming whose tickets to read.
 * <p>
 * Operators work tickets from the admin module's endpoints, which compose
 * SupportApi the way the SOS dashboard does - not from here.
 */
@RestController
@RequestMapping("/api/v1/support/tickets")
public class SupportTicketController {

    private final SupportApi supportApi;
    private final RateLimiter rateLimiter;

    SupportTicketController(SupportApi supportApi, RateLimiter rateLimiter) {
        this.supportApi = supportApi;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<TicketView> raise(@Valid @RequestBody RaiseTicketRequest request) {
        CurrentAccount caller = requireRiderOrPartner();
        // Every ticket lands in the operations queue and alerts whoever is on
        // duty. Twenty-five in a row from one account were all accepted, which
        // is a queue buried and a pager that stops meaning anything. Ten a day
        // is far past a person with real problems; a script meets it at once.
        rateLimiter.tryConsume("ticket-create:" + caller.accountId(), 10, java.time.Duration.ofHours(24))
                .orThrow("You have raised a lot of tickets today. Add to one of your open tickets, or call support.");
        Result<SupportTicketSummary, SupportError> result = supportApi.createTicket(new CreateTicketCommand(
                caller.accountId(), caller.role(), request.category(), request.subject(), request.description(),
                request.linkedBookingId()));
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketView.from(result.value()));
    }

    /** Newest activity first, filterable the same way the other history lists are. */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<TicketView>> mine(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Set<SupportTicketStatus> status,
            @RequestParam(required = false) Set<SupportTicketCategory> category,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        CurrentAccount caller = requireRiderOrPartner();
        SupportTicketQuery query = new SupportTicketQuery(caller.accountId(), status, category, null, from, to);
        return ResponseEntity.ok(PageResponse.from(
                supportApi.listTickets(query, PageRequest.of(
                        PageResponse.normalizePage(page), PageResponse.normalizePageSize(pageSize))),
                TicketView::from));
    }

    /** 404 for a ticket that is not theirs, identical to one that does not exist. */
    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketThreadView> thread(@PathVariable UUID ticketId) {
        CurrentAccount caller = requireRiderOrPartner();
        SupportTicketDetail detail = supportApi.getTicket(ticketId, caller.accountId(), caller.role())
                .orElseThrow(() -> ApiException.notFound("No such ticket"));
        return ResponseEntity.ok(TicketThreadView.from(detail, caller.accountId()));
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<MessageView> reply(@PathVariable UUID ticketId, @Valid @RequestBody ReplyRequest request) {
        CurrentAccount caller = requireRiderOrPartner();
        Result<SupportTicketMessage, SupportError> result =
                supportApi.addMessage(ticketId, caller.accountId(), caller.role(), request.message(), false);
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageView.from(result.value(), caller.accountId()));
    }

    /**
     * A role gate, so 403: it is about who the caller is, not about any
     * particular ticket, and says nothing about whether one exists.
     */
    private CurrentAccount requireRiderOrPartner() {
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER && caller.role() != AccountRole.DRIVER) {
            throw ApiException.forbidden("Support tickets are raised from the rider and partner apps");
        }
        return caller;
    }

    static ApiException toApiException(SupportError error) {
        return switch (error) {
            case TICKET_NOT_FOUND -> ApiException.notFound("No such ticket");
            // The same message PaymentController and chat use for a booking
            // that is missing or not the caller's.
            case BOOKING_NOT_FOUND -> ApiException.notFound("No booking found for this id");
            case TICKET_CLOSED -> new ApiException(HttpStatus.CONFLICT, "TICKET_CLOSED",
                    "This ticket is closed. If you still need help, raise a new one.");
            case INVALID_STATUS_TRANSITION -> new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "That status change is not allowed from the ticket's current status.");
            case ASSIGNEE_NOT_ADMIN -> new ApiException(HttpStatus.BAD_REQUEST, "ASSIGNEE_NOT_ADMIN",
                    "Tickets can only be assigned to an operations account.");
        };
    }

    public record RaiseTicketRequest(
            @NotNull SupportTicketCategory category,
            @NotBlank @Size(max = 150) String subject,
            @NotBlank @Size(max = 2000) String description,
            UUID linkedBookingId
    ) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 2000) String message) {
    }

    /**
     * A ticket as its raiser sees it. No assignee or resolver id: which
     * operator is handling it is an internal detail, and an account id means
     * nothing to the person reading it.
     */
    public record TicketView(
            UUID id,
            SupportTicketCategory category,
            String subject,
            String description,
            UUID linkedBookingId,
            SupportTicketStatus status,
            SupportTicketPriority priority,
            Instant createdAt,
            Instant lastActivityAt,
            Instant resolvedAt
    ) {
        static TicketView from(SupportTicketSummary t) {
            return new TicketView(t.id(), t.category(), t.subject(), t.description(), t.linkedBookingId(),
                    t.status(), t.priority(), t.createdAt(), t.lastActivityAt(), t.resolvedAt());
        }
    }

    /** One line of the thread. fromSupport replaces the operator's account id, which is never shown. */
    public record MessageView(UUID id, String message, boolean mine, boolean fromSupport, Instant createdAt) {
        static MessageView from(SupportTicketMessage m, UUID viewer) {
            return new MessageView(m.id(), m.message(), m.authorAccountId().equals(viewer),
                    m.authorRole() == AccountRole.ADMIN, m.createdAt());
        }
    }

    /** open is false once CLOSED, so the app hides the composer from the same rule the server enforces. */
    public record TicketThreadView(TicketView ticket, List<MessageView> messages, boolean open) {
        static TicketThreadView from(SupportTicketDetail detail, UUID viewer) {
            return new TicketThreadView(
                    TicketView.from(detail.ticket()),
                    detail.messages().stream().map(m -> MessageView.from(m, viewer)).toList(),
                    detail.ticket().status() != SupportTicketStatus.CLOSED);
        }
    }
}
