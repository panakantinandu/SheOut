package com.sheout.staff.internal;

import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * The staff audit log as other code sees it (StaffAudit). The row is written
 * and committed first (StaffAuditWriter, its own transaction); only then do
 * the alert rules look at it - an alert writes rows of its own, and doing that
 * while the first write still held the chain's lock would wait on itself.
 */
@Service
class StaffAuditLog implements StaffAudit {

    private final StaffAuditWriter writer;
    private final StaffAlerts alerts;

    StaffAuditLog(StaffAuditWriter writer, @Lazy StaffAlerts alerts) {
        this.writer = writer;
        this.alerts = alerts;
    }

    /** Set on a request that has written its own audit row, so the generic one is not added too. */
    static final String AUDITED_ATTRIBUTE = "sheout.staff.audited";

    @Override
    public void record(Entry entry) {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof
                org.springframework.web.context.request.ServletRequestAttributes attrs) {
            attrs.getRequest().setAttribute(AUDITED_ATTRIBUTE, Boolean.TRUE);
        }
        record(entry, StaffContext.current().orElse(null));
    }

    /** For a request where StaffContext is not set yet, or is no longer: signing in, a refused session. */
    void record(Entry entry, StaffPrincipal actor) {
        StaffAuditWriter.Row row = writer.append(entry, actor);
        alerts.consider(row);
    }

    /** Written without running the alert rules - the alerts' own rows. */
    void recordQuietly(Entry entry, StaffPrincipal actor) {
        writer.append(entry, actor);
    }
}
