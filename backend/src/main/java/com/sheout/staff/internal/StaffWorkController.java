package com.sheout.staff.internal;

import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresAnyPermission;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffDirectory;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.WorkAssignments;
import com.sheout.staff.WorkSla;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Taking work, giving it back, and a manager handing it to someone.
 * <ul>
 *   <li>verification - a HOLD. A verification agent opens only the partners
 *       she holds (VERIFICATION_ALL lifts that), so the queue is "take,
 *       review, decide", and nobody can take what someone else holds.</li>
 *   <li>sos, trip-alert, seller-review - a SOFT LOCK. It shows who is on it
 *       so two people do not phone the same rider, but anyone allowed to act
 *       still can: an SOS must never wait for the person who clicked first
 *       to come back. Taking one somebody else holds needs "take over".</li>
 * </ul>
 * Assigning to a named person, and giving back someone else's, is a
 * manager's (VERIFICATION_ALL for verification, SUPPORT_ALL for the rest);
 * the new holder must be able to do the work.
 */
@RestController
@RequestMapping("/api/v1/admin/work")
@RequiresAnyPermission({Permission.VERIFICATION_REVIEW, Permission.SOS_RESPOND, Permission.MARKETPLACE_MODERATE,
        Permission.SUPPORT_WORK})
class StaffWorkController {

    private final WorkAssignments work;
    private final StaffDirectory directory;
    private final WorkSla sla;

    StaffWorkController(WorkAssignments work, StaffDirectory directory, WorkSla sla) {
        this.work = work;
        this.directory = directory;
        this.sla = sla;
    }

    /** One kind of work, as the URL names it. */
    enum Queue {
        VERIFICATION("verification", WorkAssignments.Kind.VERIFICATION, Permission.VERIFICATION_REVIEW, Permission.VERIFICATION_ALL, true),
        SOS("sos", WorkAssignments.Kind.SOS, Permission.SOS_RESPOND, Permission.SUPPORT_ALL, false),
        TRIP_ALERT("trip-alert", WorkAssignments.Kind.TRIP_ALERT, Permission.SOS_RESPOND, Permission.SUPPORT_ALL, false),
        SELLER_REVIEW("seller-review", WorkAssignments.Kind.SELLER_REVIEW, Permission.MARKETPLACE_MODERATE, Permission.SUPPORT_ALL, false);

        final String path;
        final WorkAssignments.Kind kind;
        final Permission doIt;
        final Permission manage;
        final boolean hold;

        Queue(String path, WorkAssignments.Kind kind, Permission doIt, Permission manage, boolean hold) {
            this.path = path;
            this.kind = kind;
            this.doIt = doIt;
            this.manage = manage;
            this.hold = hold;
        }

        static Queue of(String path, StaffPrincipal me) {
            for (Queue q : values()) {
                if (q.path.equals(path)) {
                    if (!me.has(q.doIt)) {
                        throw StaffContext.forbidden(q.doIt);
                    }
                    return q;
                }
            }
            throw ApiException.notFound("No such queue");
        }
    }

    record Holder(UUID staffAccountId, String label, boolean you, Instant since) {
    }

    record Assign(UUID staffAccountId) {
    }

    /** How long each kind may wait, in minutes, for the clocks beside rows. */
    @GetMapping("/sla")
    ResponseEntity<WorkSla.Minutes> sla() {
        return ResponseEntity.ok(sla.minutes());
    }

    /** Who holds what in this queue, for the "taken by" beside each row. */
    @GetMapping("/{queue}")
    ResponseEntity<Map<UUID, Holder>> holders(@PathVariable String queue) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        Queue q = Queue.of(queue, me);
        Map<UUID, Holder> out = new LinkedHashMap<>();
        work.held(q.kind).forEach((subject, held) -> out.put(subject, holder(held.staffAccountId(), held.since(), me)));
        return ResponseEntity.ok(out);
    }

    /** Who a manager may hand this kind of work to. */
    @GetMapping("/{queue}/assignees")
    ResponseEntity<List<StaffDirectory.StaffMember>> assignees(@PathVariable String queue) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        Queue q = Queue.of(queue, me);
        requireManager(q, me);
        return ResponseEntity.ok(directory.activeWith(q.doIt));
    }

    @PostMapping("/{queue}/{subjectId}/take")
    ResponseEntity<Holder> take(@PathVariable String queue, @PathVariable UUID subjectId,
                                @RequestParam(defaultValue = "false") boolean takeOver) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        Queue q = Queue.of(queue, me);
        if (work.take(q.kind, subjectId, me.accountId())) {
            return ResponseEntity.ok(holder(me.accountId(), Instant.now(), me));
        }
        UUID holder = work.holder(q.kind, subjectId).orElse(null);
        String who = holder == null ? "Someone" : directory.label(holder).orElse("Someone");
        if (q.hold || !takeOver) {
            throw new ApiException(HttpStatus.CONFLICT, "TAKEN", who + (q.hold
                    ? " is already reviewing this account."
                    : " is already on this. Take it over only if they cannot."));
        }
        work.assign(q.kind, subjectId, me.accountId());
        return ResponseEntity.ok(holder(me.accountId(), Instant.now(), me));
    }

    @PostMapping("/{queue}/{subjectId}/release")
    ResponseEntity<Void> release(@PathVariable String queue, @PathVariable UUID subjectId) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        Queue q = Queue.of(queue, me);
        UUID holder = work.holder(q.kind, subjectId).orElse(null);
        if (holder != null && !holder.equals(me.accountId()) && !me.has(q.manage)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOURS", "Only whoever holds it, or a manager, can give it back.");
        }
        work.release(q.kind, subjectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{queue}/{subjectId}/assign")
    ResponseEntity<Holder> assign(@PathVariable String queue, @PathVariable UUID subjectId, @RequestBody Assign body) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        Queue q = Queue.of(queue, me);
        requireManager(q, me);
        if (body == null || body.staffAccountId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WHO", "Choose who to give it to.");
        }
        boolean able = directory.activeWith(q.doIt).stream().anyMatch(m -> m.accountId().equals(body.staffAccountId()));
        if (!able) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DO_IT", "That person cannot do this work, or is not active.");
        }
        work.assign(q.kind, subjectId, body.staffAccountId());
        return ResponseEntity.ok(holder(body.staffAccountId(), Instant.now(), me));
    }

    private static void requireManager(Queue q, StaffPrincipal me) {
        if (!me.has(q.manage)) {
            throw StaffContext.forbidden(q.manage);
        }
    }

    private Holder holder(UUID account, Instant since, StaffPrincipal me) {
        return new Holder(account, directory.label(account).orElse("Someone"), account.equals(me.accountId()), since);
    }
}
