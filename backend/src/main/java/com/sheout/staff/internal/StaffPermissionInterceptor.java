package com.sheout.staff.internal;

import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.AuditedRead;
import com.sheout.staff.Export;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresAnyPermission;
import com.sheout.staff.RequiresPermission;
import com.sheout.staff.RequiresStepUp;
import com.sheout.staff.StaffAudit;
import com.sheout.staff.StaffContext;
import com.sheout.staff.StaffPrincipal;
import com.sheout.staff.StaffPublic;
import com.sheout.staff.StaffSignedIn;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * THE authorisation check for the console, and the writer of most of its
 * audit log.
 * <p>
 * Before the endpoint: the declared permission (deny by default - an
 * undeclared console endpoint is refused, and AdminEndpointGuard stops the
 * server starting with one), then for the sensitive ones a fresh
 * authenticator code on this session. Runs before arguments or bodies are
 * read, so a refusal reveals nothing about any record. Every refusal is
 * written to the audit log as DENIED.
 * <p>
 * After the endpoint: every console change (anything but GET) is recorded
 * with how it ended, and so is every read marked {@link AuditedRead} or
 * {@link Export}. Staff-module endpoints record themselves, with more detail
 * than a URL holds (before and after of a role change), so they are skipped
 * here. A marked read that is a timed refresh (X-Staff-Background) is not
 * recorded again: the person looked once.
 */
@Component
class StaffPermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StaffPermissionInterceptor.class);
    private static final String AUDITED = StaffAuditLog.AUDITED_ATTRIBUTE;

    private final StaffAuditLog audit;
    private final StaffSessionService sessions;
    private final ApprovalService approvals;

    StaffPermissionInterceptor(StaffAuditLog audit, StaffSessionService sessions,
                               @org.springframework.context.annotation.Lazy ApprovalService approvals) {
        this.audit = audit;
        this.sessions = sessions;
        this.approvals = approvals;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Declared declared = Declared.of(method);
        boolean console = StaffSessionFilter.isConsoleApi(request);
        if (declared.none()) {
            if (console) {
                log.error("Console endpoint with no declared permission refused: {} {}", request.getMethod(), request.getRequestURI());
                throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION_NOT_DECLARED", "This endpoint is not open to anyone.");
            }
            return true;
        }
        if (declared.isPublic()) {
            return true;
        }
        StaffPrincipal staff = StaffContext.current().orElseThrow(() -> signInRequired(request));
        if (declared.signedIn()) {
            return true;
        }
        if (declared.all() != null) {
            for (Permission needed : declared.all()) {
                if (!staff.has(needed)) {
                    throw refused(staff, needed, request);
                }
            }
        }
        if (declared.any() != null && Arrays.stream(declared.any()).noneMatch(staff::has)) {
            throw refused(staff, declared.any()[0], request);
        }
        if (needsStepUp(method) && !staff.legacy() && !sessions.steppedUp(staff.sessionId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "STEP_UP_REQUIRED",
                    "Enter the code from your authenticator app to continue.");
        }
        // An export needs a second person's approval of exactly this download,
        // used once (Phase 3). Asked for on the Approvals page.
        if (method.hasMethodAnnotation(Export.class) && !staff.legacy()) {
            String path = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            String approval = request.getParameter("approval");
            UUID approvalId = null;
            try {
                approvalId = approval == null ? null : UUID.fromString(approval);
            } catch (IllegalArgumentException ignored) {
                // A malformed id is no approval.
            }
            if (approvalId == null || !approvals.consumeExport(approvalId, staff, path)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "APPROVAL_REQUIRED",
                        "Downloads of data need a second person's approval. Ask for it, then download it from the Approvals page.");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod method) || request.getAttribute(AUDITED) != null) {
            return;
        }
        Optional<StaffPrincipal> staff = StaffContext.current();
        if (staff.isEmpty() || !StaffSessionFilter.isConsoleApi(request)
                || method.getBeanType().getPackageName().startsWith("com.sheout.staff")) {
            return;
        }
        int status = ex != null && response.getStatus() < 400 ? 500 : response.getStatus();
        boolean ok = status < 400;
        Export export = method.getMethodAnnotation(Export.class);
        AuditedRead read = method.getMethodAnnotation(AuditedRead.class);
        boolean background = "1".equals(request.getHeader(StaffSessionFilter.BACKGROUND_HEADER));
        String action;
        if (export != null) {
            action = "export." + export.value();
        } else if (!"GET".equals(request.getMethod())) {
            action = request.getMethod() + " " + pattern(request);
        } else if (read != null && !background) {
            action = read.value();
        } else {
            return;
        }
        if (status == 403 && !ok) {
            // A refusal from the endpoint itself (not ours): still worth a line.
            record(staff.get(), action, firstPermission(method), StaffAudit.Result.DENIED, request);
            return;
        }
        record(staff.get(), action, firstPermission(method), ok ? StaffAudit.Result.OK : StaffAudit.Result.FAILED, request);
    }

    private void record(StaffPrincipal staff, String action, Permission permission, StaffAudit.Result result,
                        HttpServletRequest request) {
        try {
            String[] target = target(request);
            audit.record(new StaffAudit.Entry(action, permission, result, target[0], target[1], null, null), staff);
        } catch (RuntimeException e) {
            // The request already happened; failing to record it must be loud, not fatal.
            log.error("Could not write staff audit row for {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        }
    }

    private ApiException refused(StaffPrincipal staff, Permission needed, HttpServletRequest request) {
        log.warn("Staff {} ({}) refused {} {}: needs {}", staff.staffId(), staff.role(), request.getMethod(),
                request.getRequestURI(), needed.key());
        request.setAttribute(AUDITED, Boolean.TRUE);
        String[] target = target(request);
        try {
            audit.record(new StaffAudit.Entry(StaffActions.PERMISSION_DENIED, needed, StaffAudit.Result.DENIED, target[0],
                    target[1], null, request.getMethod() + " " + pattern(request)), staff);
        } catch (RuntimeException e) {
            log.error("Could not write the audit row for a refusal: {}", e.getMessage());
        }
        return StaffContext.forbidden(needed);
    }

    static boolean needsStepUp(HandlerMethod method) {
        return method.hasMethodAnnotation(RequiresStepUp.class) || method.hasMethodAnnotation(Export.class)
                || method.getBeanType().isAnnotationPresent(RequiresStepUp.class);
    }

    private static Permission firstPermission(HandlerMethod method) {
        Declared d = Declared.of(method);
        if (d.all() != null && d.all().length > 0) {
            return d.all()[0];
        }
        return d.any() != null && d.any().length > 0 ? d.any()[0] : null;
    }

    private static String pattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }

    /** The record a request was about: its first path variable, named by type ("accountId" -> ACCOUNT). */
    @SuppressWarnings("unchecked")
    private static String[] target(HttpServletRequest request) {
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map && !map.isEmpty()) {
            Map.Entry<String, String> first = ((Map<String, String>) map).entrySet().iterator().next();
            String type = first.getKey().replaceAll("Id$", "").replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
            return new String[]{type, first.getValue()};
        }
        return new String[]{null, null};
    }

    /** 401, saying why when a session existed and ended - the console shows it on the sign-in screen. */
    private static ApiException signInRequired(HttpServletRequest request) {
        Object ended = request.getAttribute(StaffSessionFilter.ENDED_ATTRIBUTE);
        if (!(ended instanceof StaffSessionService.Ended why) || why == StaffSessionService.Ended.NOT_SIGNED_IN) {
            return StaffContext.signInRequired();
        }
        String message = switch (why) {
            case IDLE -> "You were signed out after a period with no activity. Sign in again to continue.";
            case EXPIRED -> "Console sign-ins last one shift. Sign in again to continue.";
            case ACCESS_CHANGED -> "Your access to the console changed. Sign in again to continue.";
            case DISABLED -> "This staff account has been disabled. Ask an owner if you think this is a mistake.";
            case NETWORK -> "Your role can use the console only from approved networks. You have been signed out.";
            case SIGNED_OUT, NOT_SIGNED_IN -> "You've been signed out.";
        };
        return new ApiException(HttpStatus.UNAUTHORIZED, "STAFF_SESSION_ENDED", message);
    }

    /** What a handler method declares, the method's own annotation winning over its class's. */
    record Declared(boolean isPublic, boolean signedIn, Permission[] all, Permission[] any) {

        static Declared of(HandlerMethod method) {
            if (method.hasMethodAnnotation(StaffPublic.class)) {
                return new Declared(true, false, null, null);
            }
            Optional<Declared> own = fromAnnotations(
                    method.getMethodAnnotation(StaffSignedIn.class),
                    method.getMethodAnnotation(RequiresPermission.class),
                    method.getMethodAnnotation(RequiresAnyPermission.class));
            if (own.isPresent()) {
                return own.get();
            }
            Class<?> type = method.getBeanType();
            return fromAnnotations(
                    type.getAnnotation(StaffSignedIn.class),
                    type.getAnnotation(RequiresPermission.class),
                    type.getAnnotation(RequiresAnyPermission.class))
                    .orElse(new Declared(false, false, null, null));
        }

        private static Optional<Declared> fromAnnotations(StaffSignedIn signedIn, RequiresPermission all,
                                                          RequiresAnyPermission any) {
            if (signedIn == null && all == null && any == null) {
                return Optional.empty();
            }
            return Optional.of(new Declared(false, signedIn != null && all == null && any == null,
                    all == null ? null : all.value(), any == null ? null : any.value()));
        }

        boolean none() {
            return !isPublic && !signedIn && all == null && any == null;
        }
    }
}
