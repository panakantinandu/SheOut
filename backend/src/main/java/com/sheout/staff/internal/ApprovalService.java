package com.sheout.staff.internal;

import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.ApprovalExecutor;
import com.sheout.staff.Approvals;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Four-eyes requests: asking, deciding, doing. See staff.Approvals for the
 * rules; this is where they are enforced.
 * <p>
 * DECIDING AND DOING ARE SEPARATE COMMITS. The decision is written and
 * committed first (under a row lock, so two approvers cannot both decide),
 * then the owning module does the action in its own transaction, then the
 * outcome is written. A failure in the action - a payout already paid by
 * another route, a refund over the wallet's cap - leaves an approved request
 * marked FAILED with the reason, never a half-done one.
 */
@Service
class ApprovalService implements Approvals {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    /** How long an approved export may be downloaded. */
    static final Duration EXPORT_VALID_FOR = Duration.ofHours(24);

    private final ApprovalRepository approvals;
    private final List<ApprovalExecutor> executors;
    private final StaffAuditLog audit;
    private final StaffManagementService management;
    private final TransactionTemplate tx;
    private final Duration pendingFor;

    ApprovalService(ApprovalRepository approvals, @Lazy List<ApprovalExecutor> executors, StaffAuditLog audit,
                    StaffManagementService management, TransactionTemplate tx,
                    @Value("${sheout.staff.approval-hours:72}") long approvalHours) {
        this.approvals = approvals;
        this.executors = executors;
        this.audit = audit;
        this.management = management;
        this.tx = tx;
        this.pendingFor = Duration.ofHours(approvalHours);
    }

    @Override
    public Submitted submit(Request request) {
        StaffPrincipal me = requireStaff();
        String exportPath = request.kind() == Kind.EXPORT ? canonical(request.targetId()) : null;
        ApprovalEntity saved = tx.execute(status -> approvals.save(new ApprovalEntity(request, me.staffId(), me.accountId(),
                me.role().name(), Instant.now().plus(pendingFor), exportPath)));
        audit.record(new StaffAudit.Entry(StaffActions.APPROVAL_REQUEST, null, StaffAudit.Result.OK, "APPROVAL",
                saved.getId().toString(), request.reason(), "{\"kind\":\"" + request.kind() + "\",\"summary\":\""
                + jsonText(request.summary()) + "\"}"));
        return new Submitted(saved.getId(), saved.getKind(), saved.getStatus().name(), saved.getSummary(), true);
    }

    @Override
    public boolean pending(Kind kind, String targetId) {
        return approvals.existsByKindAndTargetIdAndStatusAndExpiresAtAfter(kind, targetId, ApprovalEntity.Status.PENDING, Instant.now());
    }

    /** An export: the exact request (path and parameters) it will allow once approved. */
    Submitted submitExport(String path, String reason) {
        String canonical = canonical(path);
        if (!canonical.startsWith("/api/v1/admin/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_AN_EXPORT", "That is not a console download.");
        }
        return submit(new Request(Kind.EXPORT, "Download " + canonical, "{\"path\":\"" + jsonText(canonical) + "\"}", null,
                Permission.DATA_EXPORT, "EXPORT", canonical, reason));
    }

    record Decision(UUID approvalId, String status, String message, String oneTimeValue, boolean selfApproved) {
    }

    Decision decide(UUID id, boolean approve, String note) {
        StaffPrincipal me = requireStaff();
        boolean[] self = {false};
        // Expiry is written in its own commit: the refusal that follows rolls
        // its own transaction back, and must not take the expiry with it.
        tx.executeWithoutResult(status -> approvals.findByIdForUpdate(id).ifPresent(a -> {
            if (a.expireIfDue(Instant.now())) {
                approvals.save(a);
            }
        }));
        ApprovalEntity decided = tx.execute(status -> {
            ApprovalEntity a = approvals.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("No such request"));
            if (a.getStatus() != ApprovalEntity.Status.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING", "This request has already been " + humanStatus(a.getStatus()) + ".");
            }
            Permission needed = Permission.fromKey(a.getApproverPermission()).orElseThrow();
            if (!me.has(needed)) {
                throw StaffContext.forbidden(needed);
            }
            if (a.getRequestedByStaffId().equals(me.staffId())) {
                if (!approve) {
                    throw new ApiException(HttpStatus.CONFLICT, "OWN_REQUEST", "Withdraw your own request instead.");
                }
                if (!soleOwner(me)) {
                    throw new ApiException(HttpStatus.CONFLICT, "OWN_REQUEST",
                            "Someone else has to approve this. Nobody approves their own request.");
                }
                self[0] = true;
            }
            a.decide(approve, me.staffId(), me.accountId(), note, self[0]);
            return approvals.save(a);
        });
        audit.record(new StaffAudit.Entry(approve ? StaffActions.APPROVAL_APPROVE : StaffActions.APPROVAL_REJECT, null,
                StaffAudit.Result.OK, "APPROVAL", id.toString(), note, "{\"kind\":\"" + decided.getKind() + "\"}"));
        if (self[0]) {
            audit.record(new StaffAudit.Entry(StaffActions.APPROVAL_SELF, null, StaffAudit.Result.OK, "APPROVAL", id.toString(),
                    null, jsonText(decided.getSummary())));
        }
        if (!approve) {
            return new Decision(id, decided.getStatus().name(), "Rejected.", null, false);
        }
        if (decided.getKind() == Kind.EXPORT) {
            return new Decision(id, decided.getStatus().name(),
                    "Approved. Whoever asked can download it once, within 24 hours, from the Approvals page.", null, self[0]);
        }
        ApprovalExecutor.Outcome outcome = execute(decided);
        tx.executeWithoutResult(status -> approvals.findByIdForUpdate(id).ifPresent(a -> {
            a.executed(outcome.ok(), outcome.message());
            approvals.save(a);
        }));
        audit.record(new StaffAudit.Entry(outcome.ok() ? StaffActions.APPROVAL_DONE : StaffActions.APPROVAL_FAILED, null,
                outcome.ok() ? StaffAudit.Result.OK : StaffAudit.Result.FAILED, decided.getTargetType(),
                decided.getTargetId(), null, outcome.message()));
        return new Decision(id, outcome.ok() ? "EXECUTED" : "FAILED", outcome.message(), outcome.oneTimeValue(), self[0]);
    }

    private ApprovalExecutor.Outcome execute(ApprovalEntity a) {
        Map<Kind, ApprovalExecutor> byKind = executors.stream().collect(Collectors.toMap(ApprovalExecutor::kind, Function.identity()));
        ApprovalExecutor executor = byKind.get(a.getKind());
        if (executor == null) {
            return ApprovalExecutor.Outcome.failed("Nothing on this server can carry this out.");
        }
        try {
            return executor.execute(a.getPayload());
        } catch (ApiException e) {
            return ApprovalExecutor.Outcome.failed(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Approved request {} ({}) failed to run", a.getId(), a.getKind(), e);
            return ApprovalExecutor.Outcome.failed("It could not be done: " + e.getClass().getSimpleName());
        }
    }

    void cancel(UUID id) {
        StaffPrincipal me = requireStaff();
        tx.executeWithoutResult(status -> {
            ApprovalEntity a = approvals.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("No such request"));
            if (!a.getRequestedByStaffId().equals(me.staffId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOURS", "Only whoever asked can withdraw a request.");
            }
            if (a.getStatus() != ApprovalEntity.Status.PENDING) {
                throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING", "This request has already been " + humanStatus(a.getStatus()) + ".");
            }
            a.cancel();
            approvals.save(a);
        });
        audit.record(StaffAudit.Entry.ok(StaffActions.APPROVAL_CANCEL, null, "APPROVAL", id.toString()));
    }

    /**
     * The one use of an approved export: the person who asked, the very
     * request that was approved, within a day, once.
     */
    boolean consumeExport(UUID id, StaffPrincipal me, String path) {
        String canonical = canonical(path);
        Boolean used = tx.execute(status -> {
            Optional<ApprovalEntity> found = approvals.findByIdForUpdate(id);
            if (found.isEmpty()) {
                return false;
            }
            ApprovalEntity a = found.get();
            boolean ok = a.getKind() == Kind.EXPORT && a.getStatus() == ApprovalEntity.Status.APPROVED
                    && a.getRequestedByStaffId().equals(me.staffId()) && canonical.equals(a.getExportPath())
                    && a.getExportUsedAt() == null && a.getDecidedAt() != null
                    && a.getDecidedAt().plus(EXPORT_VALID_FOR).isAfter(Instant.now());
            if (ok) {
                a.exportUsed();
                approvals.save(a);
            }
            return ok;
        });
        return Boolean.TRUE.equals(used);
    }

    /** Everything a member of staff sees on the Approvals page. */
    record Lists(List<ApprovalEntity> waiting, List<ApprovalEntity> mine, List<ApprovalEntity> recent) {
    }

    Lists lists(StaffPrincipal me) {
        Instant now = Instant.now();
        List<ApprovalEntity> pending = new ArrayList<>();
        for (ApprovalEntity a : approvals.findByStatusOrderByCreatedAtAsc(ApprovalEntity.Status.PENDING)) {
            if (a.expireIfDue(now)) {
                tx.executeWithoutResult(s -> approvals.save(a));
            } else {
                pending.add(a);
            }
        }
        boolean sole = soleOwner(me);
        List<ApprovalEntity> waiting = pending.stream()
                .filter(a -> Permission.fromKey(a.getApproverPermission()).map(me::has).orElse(false))
                .filter(a -> !a.getRequestedByStaffId().equals(me.staffId()) || sole)
                .toList();
        List<ApprovalEntity> mine = approvals.findTop50ByRequestedByStaffIdOrderByCreatedAtDesc(me.staffId());
        boolean approver = me.permissions().stream().anyMatch(p -> p == Permission.PAYOUTS_APPROVE || p == Permission.REFUNDS_APPROVE
                || p == Permission.STAFF_MANAGE_OWNER || p == Permission.DATA_EXPORT || p == Permission.INSURANCE_MANAGE);
        List<ApprovalEntity> recent = approver
                ? approvals.findTop100ByOrderByCreatedAtDesc().stream()
                    .filter(a -> a.getStatus() != ApprovalEntity.Status.PENDING)
                    .filter(a -> Permission.fromKey(a.getApproverPermission()).map(me::has).orElse(false))
                    .toList()
                : List.of();
        return new Lists(waiting, mine, recent);
    }

    /** The only active owner: the one person allowed to approve her own request, while it lasts. */
    boolean soleOwner(StaffPrincipal me) {
        return me.role() == StaffRole.OWNER && management.activeOwners() <= 1;
    }

    /**
     * A download request as a fixed string: the path and its parameters in
     * name order, without the approval id itself - so the approved request
     * and the download can be compared exactly.
     */
    static String canonical(String pathAndQuery) {
        if (pathAndQuery == null) {
            return "";
        }
        String path = pathAndQuery;
        String query = "";
        int q = pathAndQuery.indexOf('?');
        if (q >= 0) {
            path = pathAndQuery.substring(0, q);
            query = pathAndQuery.substring(q + 1);
        }
        TreeMap<String, String> params = new TreeMap<>();
        for (String pair : query.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            if (!key.equals("approval") && !value.isEmpty()) {
                params.put(key, value);
            }
        }
        return path + (params.isEmpty() ? "" : "?" + params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&")));
    }

    static String humanStatus(ApprovalEntity.Status status) {
        return switch (status) {
            case PENDING -> "asked for";
            case APPROVED -> "approved";
            case EXECUTED -> "approved and done";
            case FAILED -> "approved, but could not be done";
            case REJECTED -> "rejected";
            case CANCELLED -> "withdrawn";
            case EXPIRED -> "left too long and expired";
        };
    }

    private static StaffPrincipal requireStaff() {
        StaffPrincipal me = StaffContext.requireSignedIn();
        if (me.legacy()) {
            throw new ApiException(HttpStatus.CONFLICT, "LEGACY_SIGN_IN", "Sign in with a staff account to ask for or give approvals.");
        }
        return me;
    }

    static String jsonText(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
