package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.staff.StaffRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Every console endpoint, tried by every role that lacks what it declares,
 * through the real filters with a real session: each must answer 403
 * PERMISSION_REQUIRED, before the endpoint runs. And no console endpoint
 * exists without a declaration.
 * <p>
 * The list of endpoints is read from the running application, not written
 * down here, so an endpoint added next month is covered the day it is added.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "sheout.admin.phone-login-enabled=false", "sheout.staff.login.per-address=100000"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EveryRoleIsRefusedWhatItLacksTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffInviteService invites;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private StaffTestSupport staff;
    private final Map<StaffRole, StaffTestSupport.Session> sessions = new EnumMap<>(StaffRole.class);

    @BeforeAll
    void everyRoleSignedIn() {
        staff = new StaffTestSupport(invites, jdbc, mvc, json);
        for (StaffRole role : StaffRole.values()) {
            sessions.put(role, staff.join(role).session());
        }
    }

    @AfterAll
    void cleanUp() {
        staff.cleanUp();
    }

    @Test
    void noConsoleEndpointIsOpenByOmission() {
        assertThat(AdminEndpointGuard.problems(mappings)).isEmpty();
        long console = mappings.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPatternValues().stream().anyMatch(AdminEndpointGuard::isConsolePath))
                .count();
        assertThat(console).as("console endpoints found").isGreaterThan(90);
    }

    @Test
    void everyRoleIsRefusedEveryEndpointItLacksThePermissionFor() throws Exception {
        List<String> failures = new ArrayList<>();
        int refusalsChecked = 0;
        int allowedChecked = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            String pattern = info.getPatternValues().stream().filter(AdminEndpointGuard::isConsolePath).findFirst().orElse(null);
            if (pattern == null) {
                continue;
            }
            StaffPermissionInterceptor.Declared declared = StaffPermissionInterceptor.Declared.of(entry.getValue());
            if (declared.isPublic() || declared.signedIn()) {
                continue;
            }
            RequestMethod method = info.getMethodsCondition().getMethods().stream().findFirst().orElse(RequestMethod.GET);
            boolean multipart = info.getConsumesCondition().getConsumableMediaTypes().stream()
                    .anyMatch(MediaType.MULTIPART_FORM_DATA::includes);
            for (StaffRole role : StaffRole.values()) {
                boolean allowed = allows(declared, role);
                // Allowed roles are only tried on reads: a write with a made-up
                // id and an empty body proves nothing and might change local data.
                if (allowed && method != RequestMethod.GET) {
                    continue;
                }
                MockHttpServletResponse response = mvc.perform(build(sessions.get(role), method, pattern, multipart))
                        .andReturn().getResponse();
                boolean refusedForPermission = response.getStatus() == 403
                        && response.getContentAsString().contains("PERMISSION_REQUIRED");
                String where = role + " " + method + " " + pattern;
                if (!allowed) {
                    refusalsChecked++;
                    if (!refusedForPermission) {
                        failures.add(where + " -> " + response.getStatus() + " " + response.getContentAsString());
                    }
                } else {
                    allowedChecked++;
                    if (refusedForPermission) {
                        failures.add(where + " was refused although the role holds what it needs");
                    }
                }
            }
        }
        System.out.println("Forbidden attempts checked: " + refusalsChecked + ", allowed reads checked: " + allowedChecked);
        assertThat(failures).isEmpty();
        assertThat(refusalsChecked).isGreaterThan(400);
    }

    @Test
    void theRolesTheBriefNamesAreKeptOutOfWhatItSays() throws Exception {
        // Spot checks in plain words, on top of the exhaustive walk above.
        assertRefused(StaffRole.VERIFICATION_AGENT, HttpMethod.GET, "/api/v1/admin/payouts");
        assertRefused(StaffRole.VERIFICATION_AGENT, HttpMethod.GET, "/api/v1/admin/bookings");
        assertRefused(StaffRole.SUPPORT_AGENT, HttpMethod.GET, "/api/v1/admin/verification/" + UUID.randomUUID() + "/document");
        assertRefused(StaffRole.SAFETY_RESPONDER, HttpMethod.GET, "/api/v1/admin/payouts");
        assertRefused(StaffRole.SAFETY_RESPONDER, HttpMethod.GET, "/api/v1/admin/live");
        assertRefused(StaffRole.FINANCE, HttpMethod.GET, "/api/v1/admin/verification/documents/" + UUID.randomUUID() + "/link");
        assertRefused(StaffRole.FINANCE, HttpMethod.POST, "/api/v1/admin/payouts/" + UUID.randomUUID() + "/mark-paid");
        assertRefused(StaffRole.MANAGER, HttpMethod.POST, "/api/v1/admin/payouts/" + UUID.randomUUID() + "/mark-paid");
        assertRefused(StaffRole.MANAGER, HttpMethod.POST, "/api/v1/admin/insurance/policies");
        assertRefused(StaffRole.MARKETPLACE_MODERATOR, HttpMethod.GET, "/api/v1/admin/sos/active");
        assertRefused(StaffRole.AUDITOR, HttpMethod.POST, "/api/v1/admin/accounts/" + UUID.randomUUID() + "/block");
        assertRefused(StaffRole.AUDITOR, HttpMethod.GET, "/api/v1/admin/staff");
    }

    private void assertRefused(StaffRole role, HttpMethod method, String path) throws Exception {
        MockHttpServletResponse response = mvc.perform(sessions.get(role).on(request(method, path))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();
        assertThat(response.getStatus()).as(role + " " + method + " " + path).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PERMISSION_REQUIRED");
    }

    private static boolean allows(StaffPermissionInterceptor.Declared declared, StaffRole role) {
        boolean all = declared.all() == null || Arrays.stream(declared.all()).allMatch(role::has);
        boolean any = declared.any() == null || Arrays.stream(declared.any()).anyMatch(role::has);
        return all && any;
    }

    private static MockHttpServletRequestBuilder build(StaffTestSupport.Session session, RequestMethod method,
                                                       String pattern, boolean multipart) {
        String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        MockHttpServletRequestBuilder builder = multipart
                ? multipart(HttpMethod.valueOf(method.name()), path)
                : request(HttpMethod.valueOf(method.name()), path);
        if (!multipart && method != RequestMethod.GET) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        return session.on(builder);
    }
}
