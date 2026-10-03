package com.sheout.staff.internal;

import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffDirectory;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.WorkAssignments;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Taking a partner's verification from the queue, and giving it back.
 * <p>
 * A verification agent opens only the partners she holds (VERIFICATION_ALL
 * lifts that for managers and owners), so the queue is "take, review,
 * decide". Nobody else can take one somebody holds; a manager can release it
 * back to the queue when an agent goes off shift.
 */
@RestController
@RequestMapping("/api/v1/admin/work/verification")
@RequiresPermission(Permission.VERIFICATION_REVIEW)
class StaffWorkController {

    private final WorkAssignments work;
    private final StaffDirectory directory;

    StaffWorkController(WorkAssignments work, StaffDirectory directory) {
        this.work = work;
        this.directory = directory;
    }

    record Holder(UUID staffAccountId, String label, boolean you) {
    }

    /** Who holds which account's verification, for the queue to show beside each row. */
    @GetMapping
    ResponseEntity<Map<UUID, Holder>> holders() {
        StaffPrincipal me = StaffContext.requireSignedIn();
        Map<UUID, Holder> out = new LinkedHashMap<>();
        work.holders(WorkAssignments.Kind.VERIFICATION).forEach((subject, holder) -> out.put(subject,
                new Holder(holder, directory.label(holder).orElse("Someone"), holder.equals(me.accountId()))));
        return ResponseEntity.ok(out);
    }

    @PostMapping("/{accountId}/take")
    ResponseEntity<Holder> take(@PathVariable UUID accountId) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        if (!work.take(WorkAssignments.Kind.VERIFICATION, accountId, me.accountId())) {
            UUID holder = work.holder(WorkAssignments.Kind.VERIFICATION, accountId).orElse(null);
            throw new ApiException(HttpStatus.CONFLICT, "TAKEN",
                    (holder == null ? "Someone" : directory.label(holder).orElse("Someone")) + " is already reviewing this account.");
        }
        return ResponseEntity.ok(new Holder(me.accountId(), me.displayName(), true));
    }

    @PostMapping("/{accountId}/release")
    ResponseEntity<Void> release(@PathVariable UUID accountId) {
        StaffPrincipal me = StaffContext.requireSignedIn();
        UUID holder = work.holder(WorkAssignments.Kind.VERIFICATION, accountId).orElse(null);
        if (holder != null && !holder.equals(me.accountId()) && !me.has(Permission.VERIFICATION_ALL)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOURS", "Only whoever holds it, or a manager, can give it back.");
        }
        work.release(WorkAssignments.Kind.VERIFICATION, accountId);
        return ResponseEntity.noContent().build();
    }
}
