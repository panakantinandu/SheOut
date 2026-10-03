package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sheout.staff.StaffRole;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Real staff for integration tests, made the way production makes them: an
 * invitation, a password, an authenticator code - through the services, so a
 * test that needs eight roles does not spend the invitation rate limit.
 * Everything made is removed by {@link #cleanUp()}.
 */
final class StaffTestSupport {

    static final String PASSWORD = "tamarind-kite-onboard-71";

    private final StaffInviteService invites;
    private final JdbcTemplate jdbc;
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final List<String> emails = new ArrayList<>();
    private final List<UUID> accounts = new ArrayList<>();

    StaffTestSupport(StaffInviteService invites, JdbcTemplate jdbc, MockMvc mvc, ObjectMapper json) {
        this.invites = invites;
        this.jdbc = jdbc;
        this.mvc = mvc;
        this.json = json;
    }

    /** A signed-in console session: what the browser holds. */
    record Session(String cookie, String csrf) {

        MockHttpServletRequestBuilder on(MockHttpServletRequestBuilder request) {
            return request.cookie(new Cookie(StaffCookies.NAME, cookie)).header(StaffSessionFilter.CSRF_HEADER, csrf);
        }

        MockHttpServletRequestBuilder withoutCsrf(MockHttpServletRequestBuilder request) {
            return request.cookie(new Cookie(StaffCookies.NAME, cookie));
        }
    }

    record Member(UUID staffId, UUID accountId, String email, StaffRole role, String secret,
                  List<String> recoveryCodes, Session session) {
    }

    String newEmail(String hint) {
        String email = hint + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        emails.add(email);
        return email;
    }

    Member join(StaffRole role) {
        String email = newEmail(role.name().toLowerCase().replace('_', '-'));
        Instant expiry = role.requiresAccessExpiry() ? Instant.now().plus(30, ChronoUnit.DAYS) : null;
        StaffInviteService.Sent sent = invites.invite(email, "Test " + role, role, expiry, null, "A test").value();
        return accept(sent.link(), role);
    }

    /** Finishes an invitation from its link, as the invitee would. */
    Member accept(String link, StaffRole role) {
        String token = tokenOf(link);
        StaffInviteService.Enrolment enrolment = invites.choosePassword(token, PASSWORD).value();
        StaffInviteService.Joined joined = invites.confirmAuthenticator(token, codeNow(enrolment.secret()), "JUnit", "127.0.0.1")
                .value();
        accounts.add(joined.member().getAccountId());
        return new Member(joined.member().getId(), joined.member().getAccountId(), joined.member().getEmail(), role,
                enrolment.secret(), joined.recoveryCodes(),
                new Session(joined.opened().cookieValue(), joined.opened().session().getCsrfToken()));
    }

    static String tokenOf(String link) {
        return link.substring(link.indexOf("#invite=") + "#invite=".length());
    }

    static String codeNow(String secret) {
        return Totp.codeAt(Totp.fromBase32(secret), Totp.stepAt(Instant.now()));
    }

    /**
     * A code that will be accepted now. Each code works once, so the replay
     * guard is cleared first - only tests about the replay guard skip this.
     */
    String freshCode(Member member) {
        jdbc.update("update staff_members set totp_last_step = null where id = ?", member.staffId());
        return codeNow(member.secret());
    }

    MockHttpServletResponse signIn(String email, String password, String code) throws Exception {
        return mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password, "code", code))))
                .andReturn().getResponse();
    }

    Session signIn(Member member) throws Exception {
        MockHttpServletResponse response = signIn(member.email(), PASSWORD, freshCode(member));
        if (response.getStatus() != 200) {
            throw new AssertionError("Sign-in failed: " + response.getStatus() + " " + response.getContentAsString());
        }
        return sessionFrom(response);
    }

    Session sessionFrom(MockHttpServletResponse response) throws Exception {
        String setCookie = response.getHeader("Set-Cookie");
        String value = setCookie.substring(StaffCookies.NAME.length() + 1, setCookie.indexOf(';'));
        JsonNode body = json.readTree(response.getContentAsString());
        JsonNode me = body.has("me") ? body.get("me") : body;
        return new Session(value, me.get("csrfToken").asText());
    }

    JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    void cleanUp() {
        for (UUID account : accounts) {
            jdbc.update("delete from staff_sessions where staff_id in (select id from staff_members where account_id = ?)", account);
            jdbc.update("delete from account_sessions where account_id = ?", account);
            jdbc.update("delete from push_devices where account_id = ?", account);
        }
        for (String email : emails) {
            jdbc.update("delete from staff_invites where lower(email) = lower(?)", email);
            jdbc.update("delete from staff_members where lower(email) = lower(?)", email);
        }
        for (UUID account : accounts) {
            jdbc.update("delete from accounts where id = ?", account);
        }
        jdbc.execute("select 1");
    }

    void track(UUID accountId) {
        accounts.add(accountId);
    }
}
