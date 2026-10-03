package com.sheout.staff.internal;

import com.sheout.staff.Export;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffDirectory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * The owner's Audit screen: who did what, newest first, with alerts on top;
 * whether the chain is intact; and the log as a CSV file.
 * <p>
 * Owners only (audit.view). Reading the log is itself recorded, and the CSV
 * is an export like any other - a fresh code, an audit row, an alert to
 * every owner. (Phase 3 adds a second owner's approval to exports.)
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiresPermission(Permission.AUDIT_VIEW)
class StaffAuditController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int MAX_CSV_ROWS = 50_000;

    private final StaffAuditWriter writer;
    private final StaffAuditLog audit;
    private final StaffDirectory directory;
    private final StaffManagementService management;

    StaffAuditController(StaffAuditWriter writer, StaffAuditLog audit, StaffDirectory directory,
                         StaffManagementService management) {
        this.writer = writer;
        this.audit = audit;
        this.directory = directory;
        this.management = management;
    }

    record Row(long seq, Instant at, UUID staffId, String who, String role, String action, String permission,
               String result, String targetType, String targetId, String reason, String detail, String ipAddress,
               String device) {
    }

    record Page(List<Row> rows, List<Row> alerts, int offset, int limit) {
    }

    @GetMapping
    ResponseEntity<Page> search(@RequestParam(required = false) UUID staffId,
                                @RequestParam(required = false) String action,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "100") int limit,
                                HttpServletRequest http) {
        int size = Math.min(Math.max(limit, 1), 500);
        List<Row> rows = writer.search(staffId, action, start(from), end(to), size, Math.max(offset, 0)).stream()
                .map(this::view).toList();
        List<Row> alerts = writer.search(null, "alert.", Instant.now().minusSeconds(7 * 86_400), null, 20, 0).stream()
                .map(this::view).toList();
        if (!"1".equals(http.getHeader(StaffSessionFilter.BACKGROUND_HEADER))) {
            audit.record(new StaffAudit.Entry(StaffActions.AUDIT_VIEW, Permission.AUDIT_VIEW, StaffAudit.Result.OK, null, null,
                    null, filters(staffId, action, from, to)));
        }
        return ResponseEntity.ok(new Page(rows, alerts, offset, size));
    }

    record ChainStatus(long rowsChecked, boolean intact, Long brokenAt, String problem) {
    }

    @GetMapping("/verify")
    ResponseEntity<ChainStatus> verify() {
        StaffAuditWriter.Verification v = writer.verify();
        return ResponseEntity.ok(new ChainStatus(v.rowsChecked(), v.intact(), v.brokenAt(), v.problem()));
    }

    @Export("audit.csv")
    @GetMapping(value = "/export.csv", produces = "text/csv")
    ResponseEntity<byte[]> export(@RequestParam(required = false) UUID staffId,
                                  @RequestParam(required = false) String action,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<StaffAuditWriter.Row> rows = writer.search(staffId, action, start(from), end(to), MAX_CSV_ROWS, 0);
        StringBuilder csv = new StringBuilder("seq,at,staff,role,action,permission,result,target_type,target_id,reason,detail,ip,user_agent,hash\n");
        for (StaffAuditWriter.Row r : rows) {
            csv.append(r.seq()).append(',').append(r.occurredAt()).append(',')
                    .append(cell(r.staffId() == null ? "system" : directory.label(r.accountId()).orElse(String.valueOf(r.staffId())))).append(',')
                    .append(cell(r.staffRole())).append(',').append(cell(r.action())).append(',').append(cell(r.permission())).append(',')
                    .append(r.result()).append(',').append(cell(r.targetType())).append(',').append(cell(r.targetId())).append(',')
                    .append(cell(r.reason())).append(',').append(cell(r.detail())).append(',').append(cell(r.ipAddress())).append(',')
                    .append(cell(r.userAgent())).append(',').append(r.hash()).append('\n');
        }
        // Recorded here, not by the interceptor: staff endpoints describe themselves.
        audit.record(new StaffAudit.Entry("export.audit.csv", Permission.AUDIT_VIEW, StaffAudit.Result.OK, null, null, null,
                "{\"rows\":" + rows.size() + "," + filters(staffId, action, from, to).substring(1)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sheout-staff-audit.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Row view(StaffAuditWriter.Row r) {
        String who = r.staffId() == null ? "System"
                : management.find(r.staffId()).map(StaffMemberEntity::getDisplayName).orElse("Former staff");
        return new Row(r.seq(), r.occurredAt(), r.staffId(), who, r.staffRole(), r.action(), r.permission(), r.result(),
                r.targetType(), r.targetId(), r.reason(), r.detail(), r.ipAddress(), StaffKnownDevices.describe(r.userAgent()));
    }

    private static Instant start(LocalDate day) {
        return day == null ? null : day.atStartOfDay(IST).toInstant();
    }

    private static Instant end(LocalDate day) {
        return day == null ? null : day.plusDays(1).atStartOfDay(IST).toInstant();
    }

    private static String filters(UUID staffId, String action, LocalDate from, LocalDate to) {
        return "{\"staffId\":" + q(staffId) + ",\"action\":" + q(action) + ",\"from\":" + q(from) + ",\"to\":" + q(to) + "}";
    }

    private static String q(Object v) {
        return v == null ? "null" : "\"" + v.toString().replace("\\", "").replace("\"", "") + "\"";
    }

    /** A CSV cell, quoted, with a leading formula character neutralised for spreadsheets. */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value.matches("^[=+\\-@].*") ? "'" + value : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
