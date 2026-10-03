package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.staff.StaffRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The allowlist switched on for FINANCE only, through the real filters:
 * refused from elsewhere at sign-in, let in from the office, and signed out
 * the moment the same session is used from another network. 203.0.113.0/24
 * is a documentation range, nobody's real network.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "sheout.admin.phone-login-enabled=false", "sheout.staff.login.per-address=100000",
        "sheout.staff.ip-allowlist.finance=203.0.113.0/24"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class StaffIpAllowlistFlowTest {

    private static final RequestPostProcessor FROM_OFFICE = r -> { r.setRemoteAddr("203.0.113.40"); return r; };
    private static final RequestPostProcessor FROM_ELSEWHERE = r -> { r.setRemoteAddr("198.51.100.9"); return r; };

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffInviteService invites;

    private StaffTestSupport staff;

    @BeforeEach
    void setUp() {
        staff = new StaffTestSupport(invites, jdbc, mvc, json);
    }

    @AfterEach
    void cleanUp() {
        staff.cleanUp();
    }

    private MockHttpServletResponse signIn(StaffTestSupport.Member member, RequestPostProcessor from) throws Exception {
        return mvc.perform(post("/api/v1/admin/auth/login").with(from).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", member.email(), "password", StaffTestSupport.PASSWORD,
                        "code", staff.freshCode(member))))).andReturn().getResponse();
    }

    @Test
    void financeOnlyFromTheOfficeAndOtherRolesFromAnywhere() throws Exception {
        StaffTestSupport.Member finance = staff.join(StaffRole.FINANCE);
        StaffTestSupport.Member support = staff.join(StaffRole.SUPPORT_AGENT);

        MockHttpServletResponse away = signIn(finance, FROM_ELSEWHERE);
        assertThat(away.getStatus()).isEqualTo(403);
        assertThat(staff.body(away).get("error").asText()).isEqualTo("STAFF_NETWORK_NOT_ALLOWED");
        assertThat(signIn(support, FROM_ELSEWHERE).getStatus()).as("no list for support").isEqualTo(200);

        MockHttpServletResponse office = signIn(finance, FROM_OFFICE);
        assertThat(office.getStatus()).isEqualTo(200);
        StaffTestSupport.Session session = staff.sessionFrom(office);
        assertThat(mvc.perform(session.on(get("/api/v1/admin/payouts")).with(FROM_OFFICE))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        // The same cookie, carried to another network: refused, and the session is over.
        MockHttpServletResponse carried = mvc.perform(session.on(get("/api/v1/admin/payouts")).with(FROM_ELSEWHERE))
                .andReturn().getResponse();
        assertThat(carried.getStatus()).isEqualTo(401);
        assertThat(staff.body(carried).get("message").asText()).contains("approved networks");
        assertThat(mvc.perform(session.on(get("/api/v1/admin/payouts")).with(FROM_OFFICE))
                .andReturn().getResponse().getStatus()).as("ended, not just refused once").isEqualTo(401);
    }
}
