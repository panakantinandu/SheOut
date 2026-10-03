package com.sheout.staff.internal;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refuses to start the server if any console endpoint is open by omission.
 * <p>
 * Every endpoint under /api/v1/admin must declare what it needs
 * (RequiresPermission, RequiresAnyPermission, StaffSignedIn or StaffPublic).
 * One that forgot would otherwise be refused at runtime by the interceptor -
 * safe, but found by a confused operator rather than by the developer. This
 * makes it a failed start instead, on the developer's machine and in CI.
 * <p>
 * The opposite mistake is caught too: a permission on an endpoint OUTSIDE
 * /api/v1/admin can never be satisfied, because the console cookie is only
 * ever sent to /api/v1/admin. That endpoint is unreachable for staff, which
 * is a bug somebody should hear about.
 */
@Component
class AdminEndpointGuard {

    private final RequestMappingHandlerMapping mappings;

    AdminEndpointGuard(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        this.mappings = mappings;
    }

    @EventListener(ContextRefreshedEvent.class)
    void check() {
        List<String> problems = problems(mappings);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Console endpoints without a declared permission (see staff.RequiresPermission):\n  "
                    + String.join("\n  ", problems));
        }
    }

    static List<String> problems(RequestMappingHandlerMapping mappings) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            StaffPermissionInterceptor.Declared declared = StaffPermissionInterceptor.Declared.of(entry.getValue());
            boolean console = entry.getKey().getPatternValues().stream().anyMatch(AdminEndpointGuard::isConsolePath);
            boolean outside = entry.getKey().getPatternValues().stream().anyMatch(p -> !isConsolePath(p));
            String where = entry.getKey().getMethodsCondition() + " " + entry.getKey().getPatternValues()
                    + " -> " + entry.getValue().getShortLogMessage();
            if (console && declared.none()) {
                problems.add("no permission declared: " + where);
            }
            if (outside && !declared.none()) {
                problems.add("staff permission on an endpoint outside /api/v1/admin, which staff can never reach: " + where);
            }
        }
        problems.sort(null);
        return problems;
    }

    static boolean isConsolePath(String pattern) {
        return pattern.equals("/api/v1/admin") || pattern.startsWith("/api/v1/admin/");
    }
}
