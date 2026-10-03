package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.auth.AccountRole;
import com.sheout.staff.StaffRole;
import com.sheout.support.CreateTicketCommand;
import com.sheout.support.SupportApi;
import com.sheout.support.SupportTicketCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 2 end to end: masked by default, revealed only with a reason and a
 * fresh code, agents held to their own work, the safety desk held to open
 * alerts, refusals and reveals written down, owners told. All data is made
 * up: numbers are +9100000xxxxx, addresses invented.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "sheout.admin.phone-login-enabled=false", "sheout.staff.login.per-address=100000"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class StaffProtectionFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffInviteService invites;
    @Autowired SupportApi support;
    @Autowired StringRedisTemplate redis;

    private StaffTestSupport staff;
    private final List<UUID> riders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        staff = new StaffTestSupport(invites, jdbc, mvc, json);
        Set<String> keys = redis.keys("rl:staff-*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @AfterEach
    void cleanUp() {
        for (UUID rider : riders) {
            jdbc.update("delete from support_ticket_messages where ticket_id in (select id from support_tickets where raised_by_account_id = ?)", rider);
            jdbc.update("delete from support_tickets where raised_by_account_id = ?", rider);
            jdbc.update("delete from sos_alert where customer_account_id = ?", rider);
            jdbc.update("delete from staff_work_assignments where subject_id = ?", rider);
            jdbc.update("delete from accounts where id = ?", rider);
        }
        staff.cleanUp();
    }

    /** A rider account with a made-up number, straight into the table. */
    private UUID rider(String phone) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, phone_number, role, created_at, updated_at) values (?, ?, 'CUSTOMER', now(), now())",
                id, phone);
        riders.add(id);
        return id;
    }

    private MockHttpServletResponse reveal(StaffTestSupport.Session session, Map<String, Object> body) throws Exception {
        return mvc.perform(session.on(post("/api/v1/admin/reveal")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn().getResponse();
    }

    @Test
    void phonesAreMaskedEverywhereAndRevealedOnlyWithAFreshCodeAndAReason() throws Exception {
        UUID rider = rider("+910000020001");
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        StaffTestSupport.Session session = manager.session();

        // The account list, as a manager sees it: masked.
        String list = mvc.perform(session.on(get("/api/v1/admin/accounts")).param("q", "+910000020001"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(list).doesNotContain("0000020001").contains("00•••••001");

        Map<String, Object> ask = Map.of("subject", "ACCOUNT_PHONE", "accountId", rider.toString(), "reason", "Calling about her lost bag");

        // No fresh code: refused, and the console will ask for one.
        staff.clearStepUp(session);
        MockHttpServletResponse noCode = reveal(session, ask);
        assertThat(noCode.getStatus()).isEqualTo(403);
        assertThat(staff.body(noCode).get("error").asText()).isEqualTo("STEP_UP_REQUIRED");

        MockHttpServletResponse wrong = mvc.perform(session.on(post("/api/v1/admin/auth/step-up")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}")).andReturn().getResponse();
        assertThat(wrong.getStatus()).isEqualTo(400);
        MockHttpServletResponse right = mvc.perform(session.on(post("/api/v1/admin/auth/step-up")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("code", staff.freshCode(manager))))).andReturn().getResponse();
        assertThat(right.getStatus()).isEqualTo(200);

        // A reason is required.
        assertThat(reveal(session, Map.of("subject", "ACCOUNT_PHONE", "accountId", rider.toString(), "reason", "hi")).getStatus())
                .isEqualTo(400);

        MockHttpServletResponse shown = reveal(session, ask);
        assertThat(shown.getStatus()).isEqualTo(200);
        assertThat(staff.body(shown).get("value").asText()).isEqualTo("+910000020001");
        assertThat(jdbc.queryForObject("select reason from staff_audit_events where action = 'pii.phone.reveal' and staff_id = ?"
                + " order by seq desc limit 1", String.class, manager.staffId())).isEqualTo("Calling about her lost bag");

        // Five minutes on, the code has to be entered again.
        jdbc.update("update staff_sessions set step_up_at = now() - interval '6 minutes' where csrf_token = ?", session.csrf());
        assertThat(reveal(session, ask).getStatus()).isEqualTo(403);
    }

    @Test
    void theSafetyDeskReachesOnlyThePeopleInAnOpenOrJustClosedAlert() throws Exception {
        UUID inAlert = rider("+910000020002");
        UUID notInAlert = rider("+910000020003");
        StaffTestSupport.Member responder = staff.join(StaffRole.SAFETY_RESPONDER);
        jdbc.update("insert into sos_alert (id, customer_account_id, lat, lng, status, contacts_notified, contacts_failed,"
                + " created_at, updated_at) values (?, ?, 17.4, 78.4, 'ACTIVE', 0, 0, now(), now())", UUID.randomUUID(), inAlert);

        MockHttpServletResponse inside = reveal(responder.session(),
                Map.of("subject", "ACCOUNT_PHONE", "accountId", inAlert.toString(), "reason", "Calling her back after the SOS"));
        assertThat(inside.getStatus()).isEqualTo(200);
        MockHttpServletResponse outside = reveal(responder.session(),
                Map.of("subject", "ACCOUNT_PHONE", "accountId", notInAlert.toString(), "reason", "Just curious about her"));
        assertThat(outside.getStatus()).isEqualTo(403);
        assertThat(staff.body(outside).get("error").asText()).isEqualTo("OUT_OF_REACH");
        // And never an address: the desk does not hold that permission.
        assertThat(staff.body(reveal(responder.session(), Map.of("subject", "HOME_ADDRESS", "accountId", inAlert.toString(),
                "reason", "Where does she live"))).get("error").asText()).isEqualTo("PERMISSION_REQUIRED");

        // Closed 31 minutes ago: out of reach again.
        jdbc.update("update sos_alert set status = 'RESOLVED', resolved_at = now() - interval '31 minutes' where customer_account_id = ?", inAlert);
        assertThat(reveal(responder.session(), Map.of("subject", "ACCOUNT_PHONE", "accountId", inAlert.toString(),
                "reason", "Calling her back after the SOS")).getStatus()).isEqualTo(403);
    }

    @Test
    void anAgentWorksOnlyWhatSheHasTakenAndTwoAgentsNeverHoldTheSameOne() throws Exception {
        UUID partner = rider("+910000020004");
        StaffTestSupport.Member asha = staff.join(StaffRole.VERIFICATION_AGENT);
        StaffTestSupport.Member bina = staff.join(StaffRole.VERIFICATION_AGENT);
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        String page = "/api/v1/admin/partners/" + partner + "/verification";

        MockHttpServletResponse before = mvc.perform(asha.session().on(get(page))).andReturn().getResponse();
        assertThat(before.getStatus()).isEqualTo(403);
        assertThat(staff.body(before).get("error").asText()).isEqualTo("NOT_YOURS");

        assertThat(mvc.perform(asha.session().on(post("/api/v1/admin/work/verification/" + partner + "/take")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(asha.session().on(get(page))).andReturn().getResponse().getContentAsString()).doesNotContain("NOT_YOURS");
        assertThat(mvc.perform(bina.session().on(post("/api/v1/admin/work/verification/" + partner + "/take")))
                .andReturn().getResponse().getStatus()).as("someone else has it").isEqualTo(409);
        assertThat(mvc.perform(bina.session().on(get(page))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(bina.session().on(post("/api/v1/admin/work/verification/" + partner + "/release")))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        // A manager sees everything, without taking it, and can hand it back to the queue.
        assertThat(mvc.perform(manager.session().on(get(page))).andReturn().getResponse().getContentAsString()).doesNotContain("NOT_YOURS");
        assertThat(mvc.perform(manager.session().on(post("/api/v1/admin/work/verification/" + partner + "/release")))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    void aSupportAgentOpensOnlyTicketsSheHoldsAndCanOnlyTakeOneHerself() throws Exception {
        UUID raiser = rider("+910000020005");
        UUID ticket = support.createTicket(new CreateTicketCommand(raiser, AccountRole.CUSTOMER,
                SupportTicketCategory.values()[0], "Lost bag", "I left my bag in the cab", null)).value().id();
        StaffTestSupport.Member agent = staff.join(StaffRole.SUPPORT_AGENT);
        StaffTestSupport.Member other = staff.join(StaffRole.SUPPORT_AGENT);
        String path = "/api/v1/admin/support/tickets/" + ticket;

        assertThat(staff.body(mvc.perform(agent.session().on(get(path))).andReturn().getResponse()).get("error").asText())
                .isEqualTo("NOT_YOURS");
        assertThat(mvc.perform(agent.session().on(post(path + "/assign")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeAdminId\":\"" + other.accountId() + "\"}")).andReturn().getResponse().getStatus())
                .as("handing it to someone else").isEqualTo(403);
        assertThat(mvc.perform(agent.session().on(post(path + "/assign")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeAdminId\":\"" + agent.accountId() + "\"}")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(agent.session().on(get(path))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(other.session().on(get(path))).andReturn().getResponse().getStatus()).isEqualTo(403);
        // Her list: unassigned tickets and her own, never the other agent's.
        JsonNode otherList = staff.body(mvc.perform(other.session().on(get("/api/v1/admin/support/tickets"))).andReturn().getResponse());
        otherList.get("items").forEach(row -> assertThat(row.get("id").asText()).isNotEqualTo(ticket.toString()));
    }

    @Test
    void refusalsAndChangesAreWrittenDownAndOwnersAreTold() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member agent = staff.join(StaffRole.SUPPORT_AGENT);

        // A refusal leaves a DENIED row.
        mvc.perform(agent.session().on(get("/api/v1/admin/payouts"))).andReturn();
        assertThat(jdbc.queryForObject("select count(*) from staff_audit_events where staff_id = ? and action = 'permission.denied'"
                + " and result = 'DENIED' and permission = 'payouts.prepare'", Integer.class, agent.staffId())).isEqualTo(1);

        // A role change: recorded with before and after, and an alert.
        mvc.perform(owner.session().on(post("/api/v1/admin/staff/" + agent.staffId() + "/role")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"FINANCE\"}")).andReturn();
        assertThat(jdbc.queryForObject("select detail from staff_audit_events where action = 'staff.role.change' and target_id = ?",
                String.class, agent.staffId().toString())).contains("SUPPORT_AGENT").contains("FINANCE");
        assertThat(jdbc.queryForObject("select count(*) from staff_audit_events where action = 'alert.staff.change'"
                + " and target_id in (select seq::text from staff_audit_events where action = 'staff.role.change' and target_id = ?)",
                Integer.class, agent.staffId().toString())).isEqualTo(1);

        // A sign-in from a browser she has not used: recorded and alerted.
        MockHttpServletResponse elsewhere = mvc.perform(post("/api/v1/admin/auth/login").header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) Firefox/131.0")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", owner.email(),
                        "password", StaffTestSupport.PASSWORD, "code", staff.freshCode(owner))))).andReturn().getResponse();
        assertThat(elsewhere.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select detail from staff_audit_events where action = 'staff.device.new' and staff_id = ?"
                + " order by seq desc limit 1", String.class, owner.staffId())).isEqualTo("Firefox on Linux");

        // The owner's Audit screen shows it, and reading it is itself recorded.
        JsonNode page = staff.body(mvc.perform(owner.session().on(get("/api/v1/admin/audit")).param("staffId", owner.staffId().toString()))
                .andReturn().getResponse());
        assertThat(page.get("alerts").size()).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from staff_audit_events where action = 'audit.view' and staff_id = ?",
                Integer.class, owner.staffId())).isEqualTo(1);
        // A manager may not read it.
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        assertThat(mvc.perform(manager.session().on(get("/api/v1/admin/audit"))).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void moreThanTwentyRevealsInTenMinutesAlertsEveryOwner() throws Exception {
        UUID rider = rider("+910000020006");
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        for (int i = 0; i < StaffAlerts.REVEAL_BURST + 1; i++) {
            staff.markSteppedUp(manager.session().csrf());
            assertThat(reveal(manager.session(), Map.of("subject", "ACCOUNT_PHONE", "accountId", rider.toString(),
                    "reason", "Burst test number " + i)).getStatus()).isEqualTo(200);
        }
        // One alert for the burst, not one per reveal past the line.
        assertThat(jdbc.queryForObject("select count(*) from staff_audit_events where action = 'alert.reveal.burst' and target_id in"
                + " (select seq::text from staff_audit_events where staff_id = ? and action = 'pii.phone.reveal')",
                Integer.class, manager.staffId())).isEqualTo(1);
    }
}
