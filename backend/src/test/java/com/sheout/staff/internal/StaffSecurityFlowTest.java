package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AuthApi;
import com.sheout.auth.internal.security.JwtService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Staff sign-in and sessions end to end, on the real database and Redis,
 * through the real filters - with ADMIN_PHONE_LOGIN_ENABLED off, as in
 * production. Like the other flow tests, this needs the local Postgres and
 * Redis.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "sheout.admin.phone-login-enabled=false", "sheout.staff.login.per-address=100000"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class StaffSecurityFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffInviteService invites;
    @Autowired StringRedisTemplate redis;
    @Autowired AuthApi authApi;
    @Autowired JwtService jwt;

    private StaffTestSupport staff;

    @BeforeEach
    void setUp() {
        staff = new StaffTestSupport(invites, jdbc, mvc, json);
        // The invitation endpoints' per-address limit, from earlier runs on this machine.
        Set<String> keys = redis.keys("rl:staff-invite*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @AfterEach
    void cleanUp() {
        staff.cleanUp();
    }

    // ---- invitations -------------------------------------------------------

    @Test
    void anInvitationLinkWorksOnceAndNotAfterItExpires() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        String email = staff.newEmail("agent");

        MockHttpServletResponse sent = mvc.perform(owner.session().on(post("/api/v1/admin/staff/invites"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "displayName", "Asha", "role", "SUPPORT_AGENT"))))
                .andReturn().getResponse();
        assertThat(sent.getStatus()).isEqualTo(200);
        JsonNode sentBody = staff.body(sent);
        // No mail server locally, so the link comes back to the inviter to pass on.
        assertThat(sentBody.get("emailed").asBoolean()).isFalse();
        String token = StaffTestSupport.tokenOf(sentBody.get("link").asText());

        assertThat(lookup(token).getStatus()).isEqualTo(200);
        assertThat(staff.body(lookup(token)).get("role").asText()).isEqualTo("SUPPORT_AGENT");

        MockHttpServletResponse weak = choosePassword(token, "password12345");
        assertThat(weak.getStatus()).isEqualTo(400);
        assertThat(staff.body(weak).get("error").asText()).isEqualTo("WEAK_PASSWORD");

        MockHttpServletResponse chosen = choosePassword(token, StaffTestSupport.PASSWORD);
        assertThat(chosen.getStatus()).isEqualTo(200);
        String secret = staff.body(chosen).get("secret").asText();
        assertThat(staff.body(chosen).get("qrDataUri").asText()).startsWith("data:image/svg+xml;base64,");

        assertThat(staff.body(confirm(token, "000000")).get("error").asText()).isEqualTo("WRONG_CODE");
        MockHttpServletResponse joined = confirm(token, StaffTestSupport.codeNow(secret));
        assertThat(joined.getStatus()).isEqualTo(200);
        assertThat(staff.body(joined).get("recoveryCodes")).hasSize(10);
        assertThat(joined.getHeader("Set-Cookie")).startsWith(StaffCookies.NAME + "=");
        staff.track(UUID.fromString(jdbc.queryForObject(
                "select account_id::text from staff_members where lower(email) = lower(?)", String.class, email)));

        // Used: every step refuses it now.
        assertThat(lookup(token).getStatus()).isEqualTo(404);
        assertThat(choosePassword(token, StaffTestSupport.PASSWORD).getStatus()).isEqualTo(404);
        assertThat(confirm(token, StaffTestSupport.codeNow(secret)).getStatus()).isEqualTo(404);

        // Expired: a second invitation, aged past its 24 hours.
        String other = staff.newEmail("late");
        String lateToken = StaffTestSupport.tokenOf(
                invites.invite(other, "Late", StaffRole.FINANCE, null, owner.staffId(), "Owner").value().link());
        jdbc.update("update staff_invites set expires_at = now() - interval '1 minute' where lower(email) = lower(?)", other);
        assertThat(lookup(lateToken).getStatus()).isEqualTo(404);
        assertThat(choosePassword(lateToken, StaffTestSupport.PASSWORD).getStatus()).isEqualTo(404);
    }

    // ---- signing in ----------------------------------------------------------

    @Test
    void theSignInCookieIsHttpOnlySecureStrictAndScopedToTheConsoleApi() throws Exception {
        StaffTestSupport.Member agent = staff.join(StaffRole.VERIFICATION_AGENT);
        MockHttpServletResponse response = staff.signIn(agent.email(), StaffTestSupport.PASSWORD, staff.freshCode(agent));

        assertThat(response.getStatus()).isEqualTo(200);
        String cookie = response.getHeader("Set-Cookie");
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/v1/admin").doesNotContain("Max-Age");
        JsonNode me = staff.body(response).get("me");
        assertThat(me.get("role").asText()).isEqualTo("VERIFICATION_AGENT");
        assertThat(me.get("csrfToken").asText()).hasSizeGreaterThan(20);
        assertThat(response.getContentAsString()).doesNotContain(staff.sessionFrom(response).cookie());
    }

    @Test
    void fiveWrongAnswersLockTheAccountAndEveryRefusalReadsTheSame() throws Exception {
        StaffTestSupport.Member agent = staff.join(StaffRole.SUPPORT_AGENT);
        MockHttpServletResponse unknown = staff.signIn(staff.newEmail("nobody"), StaffTestSupport.PASSWORD, "123456");
        assertThat(unknown.getStatus()).isEqualTo(401);
        String refusal = staff.body(unknown).get("message").asText();

        for (int i = 1; i <= 4; i++) {
            MockHttpServletResponse wrong = staff.signIn(agent.email(), "wrong-password-" + i + "-xyz", staff.freshCode(agent));
            assertThat(wrong.getStatus()).isEqualTo(401);
            assertThat(staff.body(wrong).get("message").asText()).isEqualTo(refusal);
        }
        MockHttpServletResponse wrongCode = staff.signIn(agent.email(), StaffTestSupport.PASSWORD, "000000");
        assertThat(wrongCode.getStatus()).as("the fifth wrong answer locks").isEqualTo(429);

        MockHttpServletResponse right = staff.signIn(agent.email(), StaffTestSupport.PASSWORD, staff.freshCode(agent));
        assertThat(right.getStatus()).as("locked even with every answer right").isEqualTo(429);
        assertThat(jdbc.queryForObject("select locked_until > now() from staff_members where id = ?", Boolean.class,
                agent.staffId())).isTrue();
    }

    @Test
    void aRecoveryCodeWorksOnceAndSoDoesAnAuthenticatorCode() throws Exception {
        StaffTestSupport.Member finance = staff.join(StaffRole.FINANCE);
        String recovery = finance.recoveryCodes().get(3);

        MockHttpServletResponse first = staff.signIn(finance.email(), StaffTestSupport.PASSWORD, recovery.toUpperCase());
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(staff.body(first).get("usedRecoveryCode").asBoolean()).isTrue();
        assertThat(staff.body(first).get("me").get("recoveryCodesLeft").asInt()).isEqualTo(9);
        assertThat(staff.signIn(finance.email(), StaffTestSupport.PASSWORD, recovery).getStatus()).isEqualTo(401);

        String code = staff.freshCode(finance);
        assertThat(staff.signIn(finance.email(), StaffTestSupport.PASSWORD, code).getStatus()).isEqualTo(200);
        assertThat(staff.signIn(finance.email(), StaffTestSupport.PASSWORD, code).getStatus())
                .as("the same authenticator code a second time").isEqualTo(401);
    }

    // ---- the session ---------------------------------------------------------

    @Test
    void everyChangeNeedsTheCsrfTokenAndMustComeFromTheConsoleItself() throws Exception {
        StaffTestSupport.Member agent = staff.join(StaffRole.MARKETPLACE_MODERATOR);
        StaffTestSupport.Session session = agent.session();

        MockHttpServletResponse noToken = mvc.perform(session.withoutCsrf(post("/api/v1/admin/auth/logout"))).andReturn().getResponse();
        assertThat(noToken.getStatus()).isEqualTo(403);
        assertThat(staff.body(noToken).get("error").asText()).isEqualTo("CSRF_TOKEN_INVALID");

        assertThat(mvc.perform(session.withoutCsrf(post("/api/v1/admin/auth/logout")).header(StaffSessionFilter.CSRF_HEADER, "guess"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(session.on(post("/api/v1/admin/auth/logout")).header("Sec-Fetch-Site", "cross-site"))
                .andReturn().getResponse().getStatus()).as("right token, wrong site").isEqualTo(403);

        // Reading needs no token.
        assertThat(mvc.perform(session.withoutCsrf(get("/api/v1/admin/auth/me"))).andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(mvc.perform(session.on(post("/api/v1/admin/auth/logout")).header("Sec-Fetch-Site", "same-origin"))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(mvc.perform(session.on(get("/api/v1/admin/auth/me"))).andReturn().getResponse().getStatus())
                .as("signed out means the cookie is worth nothing").isEqualTo(401);
    }

    @Test
    void aSessionEndsWhenIdleOrPastItsShiftAndPollingDoesNotKeepItAlive() throws Exception {
        StaffTestSupport.Member responder = staff.join(StaffRole.SAFETY_RESPONDER);

        // Polling (X-Staff-Background) does not count as her being there.
        StaffTestSupport.Session polling = staff.signIn(responder);
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("update staff_sessions set last_activity_at = ? where csrf_token = ?", Timestamp.from(tenMinutesAgo), polling.csrf());
        assertThat(mvc.perform(polling.on(get("/api/v1/admin/sos/active")).header("X-Staff-Background", "1"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(lastActivity(polling)).isEqualTo(tenMinutesAgo);
        assertThat(mvc.perform(polling.on(get("/api/v1/admin/sos/active"))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(lastActivity(polling)).isAfter(tenMinutesAgo);

        // The safety desk's idle limit is 60 minutes (other roles 30, owners 15):
        // 31 quiet minutes is still a responder at her post, 61 is not.
        StaffTestSupport.Session idle = staff.signIn(responder);
        jdbc.update("update staff_sessions set last_activity_at = now() - interval '31 minutes' where csrf_token = ?", idle.csrf());
        assertThat(mvc.perform(idle.on(get("/api/v1/admin/sos/active"))).andReturn().getResponse().getStatus()).isEqualTo(200);
        jdbc.update("update staff_sessions set last_activity_at = now() - interval '61 minutes' where csrf_token = ?", idle.csrf());
        MockHttpServletResponse idleAnswer = mvc.perform(idle.on(get("/api/v1/admin/sos/active"))).andReturn().getResponse();
        assertThat(idleAnswer.getStatus()).isEqualTo(401);
        assertThat(staff.body(idleAnswer).get("error").asText()).isEqualTo("STAFF_SESSION_ENDED");
        assertThat(staff.body(idleAnswer).get("message").asText()).contains("no activity");
        assertThat(idleAnswer.getHeader("Set-Cookie")).contains("Max-Age=0");

        // Past the shift's end, however busy.
        StaffTestSupport.Session longShift = staff.signIn(responder);
        jdbc.update("update staff_sessions set absolute_expires_at = now() - interval '1 second' where csrf_token = ?", longShift.csrf());
        MockHttpServletResponse late = mvc.perform(longShift.on(get("/api/v1/admin/sos/active"))).andReturn().getResponse();
        assertThat(late.getStatus()).isEqualTo(401);
        assertThat(staff.body(late).get("message").asText()).contains("one shift");
    }

    @Test
    void anEndedSessionIsRefusedOnItsVeryNextRequest() throws Exception {
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        StaffTestSupport.Session laptop = staff.signIn(manager);
        StaffTestSupport.Session phone = staff.signIn(manager);
        String phoneSessionId = jdbc.queryForObject("select session_id::text from staff_sessions where csrf_token = ?",
                String.class, phone.csrf());

        assertThat(mvc.perform(phone.on(get("/api/v1/admin/auth/me"))).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(laptop.on(post("/api/v1/admin/me/sessions/" + phoneSessionId + "/end")))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(mvc.perform(phone.on(get("/api/v1/admin/auth/me"))).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(laptop.on(get("/api/v1/admin/auth/me"))).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void disablingSomeoneEndsEverySessionAndOnlyAnOwnerCanBringThemBack() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        StaffTestSupport.Member agent = staff.join(StaffRole.VERIFICATION_AGENT);
        StaffTestSupport.Session agentSession = agent.session();
        assertThat(mvc.perform(agentSession.on(get("/api/v1/admin/verification/review-queue"))).andReturn().getResponse().getStatus())
                .isEqualTo(200);

        MockHttpServletResponse disabled = mvc.perform(manager.session().on(post("/api/v1/admin/staff/" + agent.staffId() + "/disable"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Left the company\"}")).andReturn().getResponse();
        assertThat(disabled.getStatus()).isEqualTo(200);

        MockHttpServletResponse after = mvc.perform(agentSession.on(get("/api/v1/admin/verification/review-queue"))).andReturn().getResponse();
        assertThat(after.getStatus()).isEqualTo(401);
        assertThat(staff.body(after).get("message").asText()).contains("disabled");
        MockHttpServletResponse signIn = staff.signIn(agent.email(), StaffTestSupport.PASSWORD, staff.freshCode(agent));
        assertThat(signIn.getStatus()).isEqualTo(403);
        assertThat(staff.body(signIn).get("error").asText()).isEqualTo("STAFF_DISABLED");

        MockHttpServletResponse managerEnables = mvc.perform(manager.session().on(post("/api/v1/admin/staff/" + agent.staffId() + "/enable")))
                .andReturn().getResponse();
        assertThat(managerEnables.getStatus()).isEqualTo(403);
        assertThat(staff.body(managerEnables).get("error").asText()).isEqualTo("PERMISSION_REQUIRED");

        assertThat(mvc.perform(owner.session().on(post("/api/v1/admin/staff/" + agent.staffId() + "/enable")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(staff.signIn(agent.email(), StaffTestSupport.PASSWORD, staff.freshCode(agent)).getStatus()).isEqualTo(200);
    }

    @Test
    void managersManageEmployeesOnlyAndNobodyActsOnThemselves() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);

        assertThat(invite(manager, staff.newEmail("mgr2"), "MANAGER").getStatus()).isEqualTo(403);
        assertThat(invite(manager, staff.newEmail("own2"), "OWNER").getStatus()).isEqualTo(403);
        assertThat(invite(manager, staff.newEmail("ver"), "VERIFICATION_AGENT").getStatus()).isEqualTo(200);
        // An auditor's access always ends: 30 days unless a date is given, never a date already past.
        String auditorEmail = staff.newEmail("aud");
        assertThat(invite(manager, auditorEmail, "AUDITOR").getStatus()).isEqualTo(200);
        Instant auditorEnds = jdbc.queryForObject("select access_expires_at from staff_invites where lower(email) = lower(?)"
                + " and status = 'PENDING'", Timestamp.class, auditorEmail).toInstant();
        assertThat(auditorEnds).isBetween(Instant.now().plus(29, ChronoUnit.DAYS), Instant.now().plus(31, ChronoUnit.DAYS));
        MockHttpServletResponse past = mvc.perform(manager.session().on(post("/api/v1/admin/staff/invites"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", staff.newEmail("aud2"),
                        "displayName", "CA", "role", "AUDITOR", "accessExpiresAt", "2020-01-01T00:00:00Z")))).andReturn().getResponse();
        assertThat(past.getStatus()).isEqualTo(400);

        assertThat(mvc.perform(manager.session().on(post("/api/v1/admin/staff/" + owner.staffId() + "/disable"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"no\"}")).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        MockHttpServletResponse self = mvc.perform(owner.session().on(post("/api/v1/admin/staff/" + owner.staffId() + "/disable"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}")).andReturn().getResponse();
        assertThat(self.getStatus()).isEqualTo(409);
        assertThat(staff.body(self).get("error").asText()).isEqualTo("NOT_ON_YOURSELF");
        assertThat(mvc.perform(manager.session().on(post("/api/v1/admin/staff/" + manager.staffId() + "/role"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}")).andReturn().getResponse().getStatus())
                .as("promoting yourself").isEqualTo(403);

        // Out of manager is a second owner's decision (Phase 3); once approved, her
        // sessions end and the new role starts with a new sign-in.
        MockHttpServletResponse changed = mvc.perform(owner.session().on(post("/api/v1/admin/staff/" + manager.staffId() + "/role"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPPORT_AGENT\"}")).andReturn().getResponse();
        assertThat(changed.getStatus()).isEqualTo(202);
        assertThat(mvc.perform(manager.session().on(get("/api/v1/admin/auth/me"))).andReturn().getResponse().getStatus())
                .as("nothing happens until approved").isEqualTo(200);
        StaffTestSupport.Member second = staff.join(StaffRole.OWNER);
        assertThat(mvc.perform(second.session().on(post("/api/v1/admin/approvals/" + staff.body(changed).get("approvalId").asText() + "/approve")))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(manager.session().on(get("/api/v1/admin/auth/me"))).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anOwnerResetLetsHerChooseNewCredentialsAndTheOldOnesStopAtOnce() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member agent = staff.join(StaffRole.SUPPORT_AGENT);

        MockHttpServletResponse asked = mvc.perform(owner.session().on(post("/api/v1/admin/staff/" + agent.staffId() + "/reset-second-factor")))
                .andReturn().getResponse();
        assertThat(asked.getStatus()).as("a second owner decides").isEqualTo(202);
        StaffTestSupport.Member second = staff.join(StaffRole.OWNER);
        MockHttpServletResponse reset = mvc.perform(second.session().on(post("/api/v1/admin/approvals/"
                + staff.body(asked).get("approvalId").asText() + "/approve"))).andReturn().getResponse();
        assertThat(reset.getStatus()).isEqualTo(200);
        assertThat(mvc.perform(agent.session().on(get("/api/v1/admin/auth/me"))).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(staff.signIn(agent.email(), StaffTestSupport.PASSWORD, staff.freshCode(agent)).getStatus())
                .as("the old password and authenticator").isEqualTo(401);

        StaffTestSupport.Member renewed = staff.accept(staff.body(reset).get("oneTimeValue").asText(), StaffRole.SUPPORT_AGENT);
        assertThat(renewed.staffId()).isEqualTo(agent.staffId());
        assertThat(renewed.accountId()).isEqualTo(agent.accountId());
        assertThat(staff.signIn(renewed)).isNotNull();
    }

    @Test
    void sessionLengthsFollowTheRole() throws Exception {
        record Limits(int idleMinutes, long absoluteHours) {
        }
        java.util.function.Function<StaffTestSupport.Member, Limits> limits = m -> jdbc.queryForObject(
                "select idle_timeout_minutes, extract(epoch from absolute_expires_at - created_at) / 3600 as hours"
                        + " from staff_sessions where csrf_token = ?",
                (rs, n) -> new Limits(rs.getInt(1), Math.round(rs.getDouble(2))), m.session().csrf());
        assertThat(limits.apply(staff.join(StaffRole.OWNER))).isEqualTo(new Limits(15, 8));
        assertThat(limits.apply(staff.join(StaffRole.SAFETY_RESPONDER))).isEqualTo(new Limits(60, 12));
        assertThat(limits.apply(staff.join(StaffRole.SUPPORT_AGENT))).isEqualTo(new Limits(30, 8));
        // And the console is told, so its warning matches the server's clock.
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        assertThat(staff.body(mvc.perform(owner.session().on(get("/api/v1/admin/auth/me"))).andReturn().getResponse())
                .get("idleTimeoutSeconds").asLong()).isEqualTo(15 * 60);
    }

    // ---- the console is never reached any other way ---------------------------

    @Test
    void neitherAPhoneSignInNorARiderTokenReachesTheConsole() throws Exception {
        MockHttpServletResponse otp = mvc.perform(post("/api/v1/auth/otp/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"+910000000001\",\"role\":\"ADMIN\"}")).andReturn().getResponse();
        assertThat(otp.getStatus()).isEqualTo(403);
        assertThat(staff.body(otp).get("error").asText()).isEqualTo("ADMIN_PHONE_LOGIN_DISABLED");

        // An ADMIN token from before the switch: no longer a credential at all.
        UUID admin = authApi.createStaffAccount();
        staff.track(admin);
        String adminToken = jwt.issue(admin, AccountRole.ADMIN, authApi.openStaffSession(admin, "JUnit"));
        assertThat(mvc.perform(get("/api/v1/admin/bookings").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getStatus()).isEqualTo(401);

        // A rider's token, on a console endpoint.
        String riderToken = jwt.issue(UUID.randomUUID(), AccountRole.CUSTOMER, UUID.randomUUID());
        assertThat(mvc.perform(get("/api/v1/admin/bookings").header("Authorization", "Bearer " + riderToken))
                .andReturn().getResponse().getStatus()).isIn(401);
        assertThat(mvc.perform(get("/api/v1/admin/bookings")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    private Instant lastActivity(StaffTestSupport.Session session) {
        return jdbc.queryForObject("select last_activity_at from staff_sessions where csrf_token = ?", Timestamp.class,
                session.csrf()).toInstant().truncatedTo(ChronoUnit.SECONDS);
    }

    private MockHttpServletResponse invite(StaffTestSupport.Member by, String email, String role) throws Exception {
        return mvc.perform(by.session().on(post("/api/v1/admin/staff/invites")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "displayName", "Someone", "role", role))))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse lookup(String token) throws Exception {
        return mvc.perform(post("/api/v1/admin/auth/invites/lookup").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token)))).andReturn().getResponse();
    }

    private MockHttpServletResponse choosePassword(String token, String password) throws Exception {
        return mvc.perform(post("/api/v1/admin/auth/invites/password").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "password", password)))).andReturn().getResponse();
    }

    private MockHttpServletResponse confirm(String token, String code) throws Exception {
        return mvc.perform(post("/api/v1/admin/auth/invites/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", token, "code", code)))).andReturn().getResponse();
    }
}
