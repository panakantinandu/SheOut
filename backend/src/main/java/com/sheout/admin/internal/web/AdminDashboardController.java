package com.sheout.admin.internal.web;

import com.sheout.admin.internal.AdminService;
import com.sheout.admin.internal.ReviewQueueRow;
import com.sheout.booking.TripAlertsApi;
import com.sheout.notifications.SosAlertSummary;
import com.sheout.notifications.SosApi;
import com.sheout.payments.PaymentTotalsApi;
import com.sheout.payouts.PayoutApi;
import com.sheout.payouts.PayoutRequestSummary;
import com.sheout.payouts.PayoutStatus;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresAnyPermission;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffDirectory;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;
import com.sheout.staff.WorkAssignments;
import com.sheout.staff.WorkSla;
import com.sheout.support.SupportApi;
import com.sheout.support.SupportTicketQuery;
import com.sheout.support.SupportTicketStatus;
import com.sheout.support.SupportTicketSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The numbers each role's dashboard is built from, in one request. Each
 * section is filled only for a role allowed to read it, and is null
 * otherwise - a null is "not yours", never "nothing".
 * <ul>
 *   <li>ops - what is waiting on a person: SOS, trip alerts, verification,
 *       tickets, each with what is past its target (WorkSla).</li>
 *   <li>finance - today's money (India's day). Owners, finance and
 *       auditors. Not on a manager's dashboard, by the founder's brief, even
 *       though a manager may open the finance reports: this is a layout
 *       choice, not the boundary - see README "Flagged assumptions".</li>
 *   <li>team - for managers: who holds how much, and what is late in whose
 *       hands, so work can be moved before it is late rather than after.</li>
 * </ul>
 * Live trips, partners online and insurance come from their own endpoints;
 * the owners' security panel from /admin/dashboard/security (staff module).
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiresAnyPermission({Permission.REPORTS_OPS, Permission.REPORTS_FINANCE, Permission.VERIFICATION_REVIEW,
        Permission.SOS_RESPOND, Permission.SUPPORT_WORK, Permission.MARKETPLACE_MODERATE, Permission.PAYOUTS_PREPARE})
public class AdminDashboardController {

    private final AdminService admin;
    private final SosApi sos;
    private final TripAlertsApi tripAlerts;
    private final SupportApi support;
    private final PayoutApi payouts;
    private final PaymentTotalsApi totals;
    private final WorkAssignments work;
    private final StaffDirectory directory;
    private final WorkSla sla;
    private final ZoneId zone;

    public AdminDashboardController(AdminService admin, SosApi sos, TripAlertsApi tripAlerts, SupportApi support,
                                    PayoutApi payouts, PaymentTotalsApi totals, WorkAssignments work,
                                    StaffDirectory directory, WorkSla sla,
                                    @Value("${sheout.fare.timezone:Asia/Kolkata}") String timezone) {
        this.admin = admin;
        this.sos = sos;
        this.tripAlerts = tripAlerts;
        this.support = support;
        this.payouts = payouts;
        this.totals = totals;
        this.work = work;
        this.directory = directory;
        this.sla = sla;
        this.zone = ZoneId.of(timezone);
    }

    public record Dashboard(String role, Instant generatedAt, WorkSla.Minutes sla, Ops ops, Finance finance, Team team) {
    }

    /** Counts are null where the role cannot see that queue. */
    public record Ops(Integer openSos, Integer sosUnattended, Integer openTripAlerts, Integer tripAlertsUnattended,
                      Integer verificationWaiting, Instant verificationOldestSince, Integer verificationLate,
                      Integer ticketsOpen, Integer ticketsHigh, Integer ticketsLate) {
    }

    public record Finance(LocalDate day, long trips, BigDecimal fares, BigDecimal commission, BigDecimal listingFees,
                          long refundCount, BigDecimal refunds, int payoutsDue, BigDecimal payoutsDueAmount) {
    }

    public record Team(List<Agent> agents, int verificationUnassigned, int ticketsUnassigned, int escalations) {
    }

    /** One member of staff's work in hand, and how much of it is past target. */
    public record Agent(UUID accountId, String name, String role, int verificationHeld, int ticketsAssigned, int late,
                        Instant oldestSince) {
    }

    @GetMapping
    public ResponseEntity<Dashboard> dashboard() {
        StaffPrincipal me = StaffContext.requireSignedIn();
        Instant now = Instant.now();

        List<ReviewQueueRow> queue = me.has(Permission.VERIFICATION_REVIEW) ? admin.reviewQueue() : null;
        List<SosAlertSummary> openSos = me.has(Permission.SOS_RESPOND) ? sos.findActiveAlerts() : null;
        List<TripAlertsApi.TripAlert> openTrip = me.has(Permission.SOS_RESPOND) ? tripAlerts.openAlerts() : null;
        List<SupportTicketSummary> tickets = me.has(Permission.SUPPORT_ALL) || me.has(Permission.REPORTS_OPS)
                ? openTickets() : null;

        Map<UUID, WorkAssignments.Held> sosHeld = openSos == null ? Map.of() : work.held(WorkAssignments.Kind.SOS);
        Map<UUID, WorkAssignments.Held> tripHeld = openTrip == null ? Map.of() : work.held(WorkAssignments.Kind.TRIP_ALERT);

        Ops ops = new Ops(
                openSos == null ? null : openSos.size(),
                openSos == null ? null : (int) openSos.stream()
                        .filter(a -> !sosHeld.containsKey(a.id()) && late(a.createdAt(), sla.sosAck(), now)).count(),
                openTrip == null ? null : openTrip.size(),
                openTrip == null ? null : (int) openTrip.stream()
                        .filter(a -> !tripHeld.containsKey(a.id()) && late(a.raisedAt(), sla.tripAlertAck(), now)).count(),
                queue == null ? null : queue.size(),
                queue == null ? null : queue.stream().map(AdminDashboardController::waitingSince).filter(Objects::nonNull)
                        .min(Comparator.naturalOrder()).orElse(null),
                queue == null ? null : (int) queue.stream().filter(r -> late(waitingSince(r), sla.verification(), now)).count(),
                tickets == null ? null : tickets.size(),
                tickets == null ? null : (int) tickets.stream().filter(t -> "HIGH".equals(String.valueOf(t.priority()))).count(),
                tickets == null ? null : (int) tickets.stream().filter(t -> ticketLate(t, now)).count());

        Finance finance = me.has(Permission.REPORTS_FINANCE) && me.role() != StaffRole.MANAGER ? finance(now) : null;
        Team team = me.has(Permission.VERIFICATION_ALL) || me.has(Permission.SUPPORT_ALL)
                ? team(queue == null ? admin.reviewQueue() : queue, tickets == null ? openTickets() : tickets, now) : null;
        return ResponseEntity.ok(new Dashboard(me.role().name(), now, sla.minutes(), ops, finance, team));
    }

    private Finance finance(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        PaymentTotalsApi.Totals t = totals.between(today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant());
        List<PayoutRequestSummary> due = payouts.listRequests(PayoutStatus.PENDING, PageRequest.of(0, 500)).getContent();
        BigDecimal dueAmount = due.stream().map(PayoutRequestSummary::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Finance(today, t.trips(), t.fares(), t.commission(), t.listingFees(), t.refundCount(), t.refunds(),
                due.size(), dueAmount);
    }

    private Team team(List<ReviewQueueRow> queue, List<SupportTicketSummary> tickets, Instant now) {
        Map<UUID, WorkAssignments.Held> held = work.held(WorkAssignments.Kind.VERIFICATION);
        Map<UUID, ReviewQueueRow> waiting = new HashMap<>();
        queue.forEach(r -> waiting.put(r.accountId(), r));

        Map<UUID, int[]> counts = new LinkedHashMap<>();       // verification, tickets, late
        Map<UUID, Instant> oldest = new HashMap<>();
        held.forEach((subject, h) -> {
            int[] c = counts.computeIfAbsent(h.staffAccountId(), k -> new int[3]);
            c[0]++;
            ReviewQueueRow row = waiting.get(subject);
            if (row != null && late(waitingSince(row), sla.verification(), now)) {
                c[2]++;
            }
            oldest.merge(h.staffAccountId(), h.since(), (a, b) -> a.isBefore(b) ? a : b);
        });
        int ticketsUnassigned = 0;
        for (SupportTicketSummary t : tickets) {
            if (t.assignedAdminId() == null) {
                ticketsUnassigned++;
                continue;
            }
            int[] c = counts.computeIfAbsent(t.assignedAdminId(), k -> new int[3]);
            c[1]++;
            if (ticketLate(t, now)) {
                c[2]++;
            }
            oldest.merge(t.assignedAdminId(), t.createdAt(), (a, b) -> a.isBefore(b) ? a : b);
        }
        Map<UUID, StaffDirectory.StaffMember> people = new HashMap<>();
        directory.activeWith(Permission.VERIFICATION_REVIEW).forEach(m -> people.put(m.accountId(), m));
        directory.activeWith(Permission.SUPPORT_WORK).forEach(m -> people.put(m.accountId(), m));
        // Everyone who can do the work, holding something or not: an idle agent is a fact a manager needs too.
        people.keySet().forEach(id -> counts.computeIfAbsent(id, k -> new int[3]));

        List<Agent> agents = new ArrayList<>();
        counts.forEach((id, c) -> {
            StaffDirectory.StaffMember m = people.get(id);
            agents.add(new Agent(id, m != null ? m.displayName() : directory.label(id).orElse("Someone"),
                    m != null ? m.role().name() : null, c[0], c[1], c[2], oldest.get(id)));
        });
        agents.sort(Comparator.comparingInt((Agent a) -> -a.late()).thenComparingInt(a -> -(a.verificationHeld() + a.ticketsAssigned())));
        int verificationUnassigned = (int) queue.stream().filter(r -> !held.containsKey(r.accountId())).count();
        int escalations = (int) tickets.stream().filter(t -> "HIGH".equals(String.valueOf(t.priority()))).count();
        return new Team(agents, verificationUnassigned, ticketsUnassigned, escalations);
    }

    private List<SupportTicketSummary> openTickets() {
        return support.listTickets(new SupportTicketQuery(null, EnumSet.of(SupportTicketStatus.OPEN, SupportTicketStatus.IN_PROGRESS),
                null, null, null, null), PageRequest.of(0, 500)).getContent();
    }

    private boolean ticketLate(SupportTicketSummary t, Instant now) {
        return late(t.createdAt(), sla.ticket(String.valueOf(t.priority())), now);
    }

    private static Instant waitingSince(ReviewQueueRow r) {
        return r.submittedAt() != null ? r.submittedAt() : r.updatedAt();
    }

    private static boolean late(Instant since, java.time.Duration target, Instant now) {
        return since != null && since.plus(target).isBefore(now);
    }
}
