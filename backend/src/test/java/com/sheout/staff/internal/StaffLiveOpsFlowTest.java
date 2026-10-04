package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.staff.Permission;
import com.sheout.staff.StaffLiveEvents;
import com.sheout.staff.StaffRole;
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
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 4 end to end: the live channel, the work queues, the dashboards and
 * passkeys. Every account and id here is made up.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "sheout.admin.phone-login-enabled=false", "sheout.staff.login.per-address=100000"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class StaffLiveOpsFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffInviteService invites;
    @Autowired StringRedisTemplate redis;
    @Autowired StaffLiveEvents live;
    @Autowired StaffEventHub hub;

    private StaffTestSupport staff;

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
        staff.cleanUp();
    }

    private MockHttpServletResponse postJson(StaffTestSupport.Session s, String path, Object body) throws Exception {
        return mvc.perform(s.on(post(path)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse read(StaffTestSupport.Session s, String path) throws Exception {
        return mvc.perform(s.on(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))).andReturn().getResponse();
    }

    private static void eventually(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 50 && !condition.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static String text(MvcResult stream) {
        try {
            return stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    // ---- the live channel -----------------------------------------------------

    @Test
    void anSosReachesOnlyConsolesThatCanRespondAndARevokedConsoleIsCutOff() throws Exception {
        StaffTestSupport.Member responder = staff.join(StaffRole.SAFETY_RESPONDER);
        StaffTestSupport.Member finance = staff.join(StaffRole.FINANCE);

        MvcResult responderStream = mvc.perform(responder.session().withoutCsrf(get("/api/v1/admin/events"))).andReturn();
        MvcResult financeStream = mvc.perform(finance.session().withoutCsrf(get("/api/v1/admin/events"))).andReturn();
        assertThat(responderStream.getRequest().isAsyncStarted()).isTrue();
        assertThat(responderStream.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        eventually(() -> text(responderStream).contains("event:hello") && text(financeStream).contains("event:hello"));

        String alertId = UUID.randomUUID().toString();
        live.publish("sos", Permission.SOS_RESPOND, "{\"alertId\":\"" + alertId + "\",\"duringTrip\":true}");
        eventually(() -> text(responderStream).contains(alertId));
        Thread.sleep(300);
        // A nudge with ids only, and only to who can act on it.
        assertThat(text(financeStream)).doesNotContain("event:sos").doesNotContain(alertId);

        // Signed out elsewhere: the next check closes the stream with a reason.
        postJson(responder.session(), "/api/v1/admin/me/sessions/end-all", Map.of());
        hub.recheck();
        assertThat(text(responderStream)).contains("event:signed-out");
        String second = UUID.randomUUID().toString();
        live.publish("sos", Permission.SOS_RESPOND, "{\"alertId\":\"" + second + "\"}");
        Thread.sleep(500);
        assertThat(text(responderStream)).doesNotContain(second);

        // Without a session there is no stream at all.
        assertThat(mvc.perform(get("/api/v1/admin/events")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    // ---- queues ---------------------------------------------------------------

    @Test
    void softLocksShowWhoIsOnItAndOnlyAManagerHandsWorkOut() throws Exception {
        StaffTestSupport.Member one = staff.join(StaffRole.SAFETY_RESPONDER);
        StaffTestSupport.Member two = staff.join(StaffRole.SAFETY_RESPONDER);
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        StaffTestSupport.Member agent = staff.join(StaffRole.VERIFICATION_AGENT);
        StaffTestSupport.Member support = staff.join(StaffRole.SUPPORT_AGENT);
        String sos = UUID.randomUUID().toString();

        assertThat(postJson(one.session(), "/api/v1/admin/work/sos/" + sos + "/take", Map.of()).getStatus()).isEqualTo(200);
        MockHttpServletResponse clash = postJson(two.session(), "/api/v1/admin/work/sos/" + sos + "/take", Map.of());
        assertThat(clash.getStatus()).isEqualTo(409);
        assertThat(staff.body(clash).get("message").asText()).contains("Take it over only if they cannot");
        // A soft lock: the second responder can still take it over when the first cannot answer.
        assertThat(postJson(two.session(), "/api/v1/admin/work/sos/" + sos + "/take?takeOver=true", Map.of()).getStatus()).isEqualTo(200);
        JsonNode holders = staff.body(read(one.session(), "/api/v1/admin/work/sos"));
        assertThat(holders.get(sos).get("staffAccountId").asText()).isEqualTo(two.accountId().toString());
        assertThat(holders.get(sos).get("since").asText()).isNotBlank();

        // A role that cannot do the work cannot see the queue.
        assertThat(read(agent.session(), "/api/v1/admin/work/sos").getStatus()).isEqualTo(403);
        assertThat(read(support.session(), "/api/v1/admin/work/verification").getStatus()).isEqualTo(403);

        // Verification is a hold: nobody takes it over, but a manager can hand it to a named agent.
        String partner = UUID.randomUUID().toString();
        assertThat(postJson(agent.session(), "/api/v1/admin/work/verification/" + partner + "/assign",
                Map.of("staffAccountId", agent.accountId().toString())).getStatus()).isEqualTo(403);
        assertThat(postJson(manager.session(), "/api/v1/admin/work/verification/" + partner + "/assign",
                Map.of("staffAccountId", support.accountId().toString())).getStatus()).isEqualTo(400);
        assertThat(postJson(manager.session(), "/api/v1/admin/work/verification/" + partner + "/assign",
                Map.of("staffAccountId", agent.accountId().toString())).getStatus()).isEqualTo(200);
        assertThat(staff.body(read(agent.session(), "/api/v1/admin/work/verification")).get(partner).get("you").asBoolean()).isTrue();
        assertThat(postJson(one.session(), "/api/v1/admin/work/verification/" + partner + "/take?takeOver=true", Map.of())
                .getStatus()).isEqualTo(403);
        Integer audited = jdbc.queryForObject("select count(*) from staff_audit_events where action = 'work.assign.verification'"
                + " and target_id = ?", Integer.class, partner);
        assertThat(audited).isEqualTo(1);

        JsonNode sla = staff.body(read(agent.session(), "/api/v1/admin/work/sla"));
        assertThat(sla.get("verification").asLong()).isEqualTo(24 * 60);
        assertThat(sla.get("sosAck").asLong()).isEqualTo(2);
        jdbc.update("delete from staff_work_assignments where subject_id in (?::uuid, ?::uuid)", sos, partner);
    }

    // ---- dashboards -----------------------------------------------------------

    @Test
    void eachRoleGetsItsOwnDashboard() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        StaffTestSupport.Member agent = staff.join(StaffRole.VERIFICATION_AGENT);
        StaffTestSupport.Member finance = staff.join(StaffRole.FINANCE);

        JsonNode mine = staff.body(read(owner.session(), "/api/v1/admin/dashboard"));
        assertThat(mine.get("finance").get("fares")).isNotNull();
        assertThat(mine.get("team").get("agents").isArray()).isTrue();
        assertThat(mine.get("ops").get("openSos").isNull()).isFalse();

        JsonNode managers = staff.body(read(manager.session(), "/api/v1/admin/dashboard"));
        assertThat(managers.get("finance").isNull()).isTrue();
        assertThat(managers.get("team").get("agents").toString()).contains(agent.accountId().toString());

        JsonNode agents = staff.body(read(agent.session(), "/api/v1/admin/dashboard"));
        assertThat(agents.get("team").isNull()).isTrue();
        assertThat(agents.get("finance").isNull()).isTrue();
        assertThat(agents.get("ops").get("openSos").isNull()).isTrue();
        assertThat(agents.get("ops").get("verificationWaiting").isNull()).isFalse();

        assertThat(staff.body(read(finance.session(), "/api/v1/admin/dashboard")).get("finance").get("refunds")).isNotNull();

        // The security panel is the owners' alone.
        JsonNode security = staff.body(read(owner.session(), "/api/v1/admin/dashboard/security"));
        assertThat(security.get("onlineNow").toString()).contains(owner.staffId().toString());
        assertThat(security.get("passkeysMissing").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(read(manager.session(), "/api/v1/admin/dashboard/security").getStatus()).isEqualTo(403);
    }

    // ---- passkeys -------------------------------------------------------------

    @Test
    void anOwnerWithoutAPasskeyCanOnlyAddOne() throws Exception {
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        // An owner made without the test helper's passkey: as she is on her first sign-in.
        StaffInviteService.Sent sent = invites.invite(staff.newEmail("bare-owner"), "Bare Owner", StaffRole.OWNER, null, null, "A test").value();
        String token = StaffTestSupport.tokenOf(sent.link());
        StaffInviteService.Enrolment enrolment = invites.choosePassword(token, StaffTestSupport.PASSWORD).value();
        StaffInviteService.Joined joined = invites.confirmAuthenticator(token, StaffTestSupport.codeNow(enrolment.secret()),
                "JUnit", "127.0.0.1").value();
        staff.track(joined.member().getAccountId());
        StaffTestSupport.Session owner = new StaffTestSupport.Session(joined.opened().cookieValue(),
                joined.opened().session().getCsrfToken());

        MockHttpServletResponse blocked = read(owner, "/api/v1/admin/staff");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(staff.body(blocked).get("error").asText()).isEqualTo("PASSKEY_REQUIRED");
        JsonNode me = staff.body(read(owner, "/api/v1/admin/auth/me"));
        assertThat(me.get("passkey").get("required").asBoolean()).isTrue();
        assertThat(me.get("passkey").get("count").asInt()).isZero();
        assertThat(staff.body(read(manager.session(), "/api/v1/admin/auth/me")).get("passkey").get("recommended").asBoolean()).isTrue();

        // Adding one needs a fresh step-up: a borrowed session cannot plant its own key.
        jdbc.update("update staff_sessions set step_up_at = null where csrf_token = ?", owner.csrf());
        MockHttpServletResponse noStepUp = postJson(owner, "/api/v1/admin/me/passkeys/options", Map.of());
        assertThat(staff.body(noStepUp).get("error").asText()).isEqualTo("STEP_UP_REQUIRED");
        staff.markSteppedUp(owner.csrf());
        SoftPasskey key = staff.addPasskey(joined.member().getId(), owner);

        assertThat(read(owner, "/api/v1/admin/staff").getStatus()).isEqualTo(200);
        Integer alerted = jdbc.queryForObject("select count(*) from staff_audit_events where action = 'alert.passkey'"
                + " and occurred_at > now() - interval '1 minute'", Integer.class);
        assertThat(alerted).isGreaterThanOrEqualTo(1);

        // Her only passkey cannot be removed while her role needs one.
        String id = staff.body(read(owner, "/api/v1/admin/me/passkeys")).get("passkeys").get(0).get("id").asText();
        assertThat(staff.body(postJson(owner, "/api/v1/admin/me/passkeys/" + id + "/remove", Map.of())).get("error").asText())
                .isEqualTo("LAST_PASSKEY");
        assertThat(key).isNotNull();
    }

    @Test
    void anOwnerSignsInWithHerPasskeyAndOnlyHerPasskey() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member other = staff.join(StaffRole.OWNER);

        // With a passkey, the authenticator code alone is not enough for an owner.
        MockHttpServletResponse withCode = staff.signIn(owner.email(), StaffTestSupport.PASSWORD, staff.freshCode(owner));
        assertThat(withCode.getStatus()).isEqualTo(403);
        assertThat(staff.body(withCode).get("error").asText()).isEqualTo("STAFF_USE_PASSKEY");

        MockHttpServletResponse ok = staff.signInWithPasskey(owner.email(), StaffTestSupport.PASSWORD, owner.passkey());
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select detail from staff_audit_events where action = 'staff.signin' and target_id = ?"
                + " order by seq desc limit 1", String.class, owner.staffId().toString())).isEqualTo("with a passkey");

        // Another owner's passkey does not open this account, even with the right password.
        assertThat(staff.signInWithPasskey(owner.email(), StaffTestSupport.PASSWORD, other.passkey()).getStatus()).isEqualTo(401);
        // Wrong password with the right passkey: refused the same way.
        assertThat(staff.signInWithPasskey(owner.email(), "not-the-password-at-all", owner.passkey()).getStatus()).isEqualTo(401);

        // An answer is good once, for this site only.
        JsonNode options = staff.body(mvc.perform(post("/api/v1/admin/auth/passkey/options")).andReturn().getResponse());
        String answer = owner.passkey().sign(options.get("publicKey"));
        Map<String, String> login = Map.of("email", owner.email(), "password", StaffTestSupport.PASSWORD,
                "passkeyChallengeId", options.get("challengeId").asText(), "passkeyCredential", answer);
        assertThat(mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login))).andReturn().getResponse().getStatus()).isEqualTo(401);
        JsonNode phished = staff.body(mvc.perform(post("/api/v1/admin/auth/passkey/options")).andReturn().getResponse());
        assertThat(mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", owner.email(), "password", StaffTestSupport.PASSWORD,
                        "passkeyChallengeId", phished.get("challengeId").asText(),
                        "passkeyCredential", owner.passkey().sign(phished.get("publicKey"), "https://sheout-console.example.net")))))
                .andReturn().getResponse().getStatus()).isEqualTo(401);

        // Five refusals for one email is the limit itself; start a fresh window.
        setUp();
        // A lost laptop is not a lockout: a recovery code still works.
        MockHttpServletResponse recovered = staff.signIn(owner.email(), StaffTestSupport.PASSWORD, owner.recoveryCodes().get(0));
        assertThat(recovered.getStatus()).as(recovered.getContentAsString()).isEqualTo(200);

        // Step-up with the passkey instead of a code.
        StaffTestSupport.Session session = staff.signIn(owner);
        staff.clearStepUp(session);
        JsonNode stepOptions = staff.body(postJson(session, "/api/v1/admin/auth/step-up/passkey-options", Map.of()));
        assertThat(stepOptions.get("publicKey").get("allowCredentials").size()).isEqualTo(1);
        assertThat(postJson(session, "/api/v1/admin/auth/step-up", Map.of("passkeyChallengeId", stepOptions.get("challengeId").asText(),
                "passkeyCredential", owner.passkey().sign(stepOptions.get("publicKey")))).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select step_up_at is not null from staff_sessions where csrf_token = ?", Boolean.class,
                session.csrf())).isTrue();
    }

    @Test
    void theSignInChallengeSaysNothingAboutWhoIsOnTheStaff() throws Exception {
        JsonNode options = staff.body(mvc.perform(post("/api/v1/admin/auth/passkey/options")).andReturn().getResponse());
        assertThat(options.get("publicKey").has("allowCredentials")
                && options.get("publicKey").get("allowCredentials").size() > 0).isFalse();
        assertThat(options.get("publicKey").get("rpId").asText()).isEqualTo("localhost");
        assertThat(options.get("publicKey").get("userVerification").asText()).isEqualTo("required");
    }
}
