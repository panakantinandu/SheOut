package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.driververification.internal.CourtScreeningService;
import com.sheout.staff.StaffRole;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The court-record screening box through the real console: real sessions,
 * the permission check, CSRF and the audit filter. Three reviewers, because
 * the rules are about who may do what: the one who flagged a match cannot
 * sign it off or decide it.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "sheout.admin.phone-login-enabled=false", "sheout.staff.login.per-address=100000",
        "sheout.verification.court-screen.enabled=true", "sheout.verification.court-screen.consent-version=test-v1"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtScreeningConsoleTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffInviteService invites;
    @Autowired CourtScreeningService screenings;

    private StaffTestSupport staff;
    private StaffTestSupport.Session a;
    private StaffTestSupport.Session b;
    private StaffTestSupport.Session c;
    private StaffTestSupport.Session support;
    private final UUID partner = UUID.randomUUID();

    @BeforeAll
    void setUp() {
        staff = new StaffTestSupport(invites, jdbc, mvc, json);
        a = staff.join(StaffRole.MANAGER).session();
        b = staff.join(StaffRole.MANAGER).session();
        c = staff.join(StaffRole.MANAGER).session();
        support = staff.join(StaffRole.SUPPORT_AGENT).session();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into verification_records (id, account_id, role, gender_verification_status, created_at, updated_at) "
                + "values (?, ?, 'DRIVER', 'VERIFIED', ?, ?)", UUID.randomUUID(), partner, now, now);
    }

    @AfterAll
    void cleanUp() {
        jdbc.update("delete from court_screening_searches where screening_id in (select id from court_screenings where account_id = ?)", partner);
        jdbc.update("delete from court_screenings where account_id = ?", partner);
        jdbc.update("delete from verification_checks where account_id = ?", partner);
        jdbc.update("delete from verification_audit_events where account_id = ?", partner);
        jdbc.update("delete from verification_records where account_id = ?", partner);
        staff.cleanUp();
    }

    private String base() {
        return "/api/v1/admin/court-screenings/" + partner;
    }

    @Test
    void theWholeReviewerFlowFollowsTheRules() throws Exception {
        // A role without verification.review is refused before anything runs.
        mvc.perform(support.on(get(base()))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PERMISSION_REQUIRED"));
        // Nothing yet: the same 404 as a partner who does not exist.
        mvc.perform(a.on(get(base()))).andExpect(status().isNotFound());

        mvc.perform(a.on(post(base()).contentType(MediaType.APPLICATION_JSON).content("{\"extraDistricts\":[\"Warangal\"]}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONSENT_REQUESTED"))
                .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("not police verification")));

        String possible = "{\"district\":\"Hyderabad\",\"nameVariant\":\"Lakshmi Devi\",\"outcome\":\"POSSIBLE_MATCH\","
                + "\"note\":\"Same name and father's name\",\"caseType\":\"Sec 379\",\"caseStatus\":\"Pending\",\"caseYear\":2021}";
        mvc.perform(a.on(post(base() + "/searches").contentType(MediaType.APPLICATION_JSON).content(possible)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CONSENT_REQUIRED"));

        screenings.consent(partner, "test-v1");

        mvc.perform(a.on(post(base() + "/searches").contentType(MediaType.APPLICATION_JSON).content(possible)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("POSSIBLE_MATCH"));
        mvc.perform(a.on(post(base() + "/second-signoff").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agree\":true,\"note\":\"agree\"}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SAME_REVIEWER"));
        mvc.perform(b.on(post(base() + "/second-signoff").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agree\":true,\"note\":\"Age and district fit\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCH_CONFIRMED"));

        String reject = "{\"decision\":\"REJECT\",\"reasonCategory\":\"RECORD_CONFIRMED\"}";
        mvc.perform(c.on(post(base() + "/decision").contentType(MediaType.APPLICATION_JSON).content(reject)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PARTNER_NOT_HEARD"));
        mvc.perform(b.on(post(base() + "/notify"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_RESPONSE"));
        screenings.respond(partner, "It is not me - a cousin with the same name.");
        mvc.perform(a.on(post(base() + "/decision").contentType(MediaType.APPLICATION_JSON).content(reject)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("OWN_FLAGGED_CASE"));
        mvc.perform(c.on(post(base() + "/decision").contentType(MediaType.APPLICATION_JSON).content(reject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reasonCategory").value("RECORD_CONFIRMED"));

        mvc.perform(a.on(get("/api/v1/admin/court-screenings/report"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.flagged").isNumber());
    }
}
