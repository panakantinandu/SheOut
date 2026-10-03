package com.sheout.staff.internal;

import com.sheout.sharedkernel.web.ApiException;
import com.sheout.staff.Permission;
import com.sheout.staff.RequiresAnyPermission;
import com.sheout.staff.RequiresPermission;
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

import java.util.Arrays;
import java.util.Optional;

/**
 * THE authorisation check for the console: the one place a console endpoint's
 * declared permission is compared with what the signed-in member of staff
 * holds. It replaces the twenty-odd requireAdmin() copies that each said "is
 * this an ADMIN" and nothing more.
 * <p>
 * Runs before the endpoint, its arguments or its request body are looked at,
 * so a refused request reveals nothing about any record.
 * <p>
 * DENY BY DEFAULT. A console endpoint with no declaration is refused here,
 * and AdminEndpointGuard stops the server starting with one in the first
 * place.
 * <p>
 * Refusals are logged with who, what was needed and where; Phase 2 writes
 * each to the audit log as well.
 */
@Component
class StaffPermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StaffPermissionInterceptor.class);

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
        return true;
    }

    private static ApiException refused(StaffPrincipal staff, Permission needed, HttpServletRequest request) {
        log.warn("Staff {} ({}) refused {} {}: needs {}", staff.staffId(), staff.role(), request.getMethod(),
                request.getRequestURI(), needed.key());
        return StaffContext.forbidden(needed);
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
