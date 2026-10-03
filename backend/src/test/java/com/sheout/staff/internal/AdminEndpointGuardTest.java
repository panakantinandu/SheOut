package com.sheout.staff.internal;

import com.sheout.staff.Permission;
import com.sheout.staff.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The start-up check, shown failing on purpose for both mistakes it exists to catch. */
class AdminEndpointGuardTest {

    @RestController
    static class Forgetful {
        @GetMapping("/api/v1/admin/forgot-to-say")
        String open() {
            return "anyone";
        }

        @RequiresPermission(Permission.TRIPS_VIEW)
        @GetMapping("/api/v1/admin/said-so")
        String guarded() {
            return "staff";
        }

        @RequiresPermission(Permission.TRIPS_VIEW)
        @GetMapping("/api/v1/bookings/staff-only")
        String unreachable() {
            return "nobody";
        }
    }

    @Test
    void anUndeclaredConsoleEndpointAndAnUnreachableOneAreBothReported() {
        RequestMappingHandlerMapping mappings = mappingsFor(Forgetful.class);

        List<String> problems = AdminEndpointGuard.problems(mappings);

        assertThat(problems).hasSize(2);
        assertThat(problems).anyMatch(p -> p.startsWith("no permission declared") && p.contains("/api/v1/admin/forgot-to-say"));
        assertThat(problems).anyMatch(p -> p.contains("outside /api/v1/admin") && p.contains("/api/v1/bookings/staff-only"));
        assertThatThrownBy(() -> new AdminEndpointGuard(mappings).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forgot-to-say");
    }

    private static RequestMappingHandlerMapping mappingsFor(Class<?> controller) {
        StaticWebApplicationContext context = new StaticWebApplicationContext();
        context.registerSingleton("controller", controller);
        context.refresh();
        RequestMappingHandlerMapping mappings = new RequestMappingHandlerMapping();
        mappings.setApplicationContext(context);
        mappings.afterPropertiesSet();
        return mappings;
    }
}
