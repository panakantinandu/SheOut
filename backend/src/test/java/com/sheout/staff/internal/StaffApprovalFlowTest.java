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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 3 end to end: what one person cannot do alone. All accounts and
 * numbers are made up.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "sheout.admin.phone-login-enabled=false", "sheout.staff.login.per-address=100000"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
class StaffApprovalFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StaffInviteService invites;
    @Autowired SupportApi support;
    @Autowired StringRedisTemplate redis;

    private StaffTestSupport staff;
    private final List<UUID> riders = new ArrayList<>();
    private final List<UUID> payouts = new ArrayList<>();

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
        for (UUID p : payouts) {
            jdbc.update("delete from payout_requests where id = ?", p);
        }
        for (UUID rider : riders) {
            jdbc.update("delete from support_ticket_messages where ticket_id in (select id from support_tickets where raised_by_account_id = ?)", rider);
            jdbc.update("delete from support_tickets where raised_by_account_id = ?", rider);
            jdbc.update("delete from rider_wallet_entries where customer_account_id = ?", rider);
            jdbc.update("delete from rider_wallets where customer_account_id = ?", rider);
            jdbc.update("delete from accounts where id = ?", rider);
        }
        staff.cleanUp();
    }

    private UUID rider(String phone) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into accounts (id, phone_number, role, created_at, updated_at) values (?, ?, 'CUSTOMER', now(), now())", id, phone);
        riders.add(id);
        return id;
    }

    private MockHttpServletResponse postJson(StaffTestSupport.Session s, String path, Object body) throws Exception {
        return mvc.perform(s.on(post(path)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse approve(StaffTestSupport.Member who, String approvalId) throws Exception {
        staff.markSteppedUp(who.session().csrf());
        return postJson(who.session(), "/api/v1/admin/approvals/" + approvalId + "/approve", Map.of("note", "Checked"));
    }

    private BigDecimal wallet(UUID rider) {
        return jdbc.query("select balance from rider_wallets where customer_account_id = ?", rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, rider);
    }

    @Test
    void refundsUpToTheLimitAreImmediateAndAboveItAnOwnerDecides() throws Exception {
        UUID rider = rider("+910000030001");
        UUID ticket = support.createTicket(new CreateTicketCommand(rider, AccountRole.CUSTOMER, SupportTicketCategory.values()[0],
                "Overcharged", "The trip cost more than quoted", null)).value().id();
        StaffTestSupport.Member agent = staff.join(StaffRole.SUPPORT_AGENT);
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        staff.join(StaffRole.OWNER);

        // Not her ticket yet: out of reach.
        Map<String, Object> small = Map.of("customerAccountId", rider.toString(), "ticketId", ticket.toString(), "amount", 150,
                "reason", "Goodwill for the long wait");
        assertThat(postJson(agent.session(), "/api/v1/admin/refunds", small).getStatus()).isEqualTo(403);
        postJson(agent.session(), "/api/v1/admin/support/tickets/" + ticket + "/assign", Map.of("assigneeAdminId", agent.accountId().toString()));

        MockHttpServletResponse issued = postJson(agent.session(), "/api/v1/admin/refunds", small);
        assertThat(issued.getStatus()).isEqualTo(200);
        assertThat(staff.body(issued).get("status").asText()).isEqualTo("ISSUED");
        assertThat(wallet(rider)).isEqualByComparingTo("150.00");

        // Above her 200: waits for an owner.
        MockHttpServletResponse big = postJson(agent.session(), "/api/v1/admin/refunds", Map.of("customerAccountId", rider.toString(),
                "ticketId", ticket.toString(), "amount", 500, "reason", "Refund the whole trip"));
        assertThat(big.getStatus()).isEqualTo(202);
        String approvalId = staff.body(big).get("approvalId").asText();
        assertThat(wallet(rider)).as("nothing yet").isEqualByComparingTo("150.00");

        // A manager may not approve refunds above the limit; an owner may.
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);
        assertThat(approve(manager, approvalId).getStatus()).isEqualTo(403);
        MockHttpServletResponse done = approve(owner, approvalId);
        assertThat(staff.body(done).get("status").asText()).isEqualTo("EXECUTED");
        assertThat(wallet(rider)).isEqualByComparingTo("650.00");
        // Decided once.
        assertThat(approve(owner, approvalId).getStatus()).isEqualTo(409);
    }

    @Test
    void financeRecordsAPayoutAndOnlySomeoneElseCanMarkItPaid() throws Exception {
        UUID payout = UUID.randomUUID();
        UUID partner = UUID.randomUUID();
        jdbc.update("insert into payout_requests (id, driver_account_id, amount, status, upi_vpa, created_at, updated_at)"
                + " values (?, ?, 420.00, 'PENDING', 'partner@upi', now(), now())", payout, partner);
        payouts.add(payout);
        StaffTestSupport.Member finance = staff.join(StaffRole.FINANCE);
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);

        assertThat(postJson(manager.session(), "/api/v1/admin/payouts/" + payout + "/mark-paid", Map.of("paymentReference", "UTR000000000001"))
                .getStatus()).as("a manager approves, she does not record").isEqualTo(403);
        MockHttpServletResponse asked = postJson(finance.session(), "/api/v1/admin/payouts/" + payout + "/mark-paid",
                Map.of("paymentReference", "UTR000000000001"));
        assertThat(asked.getStatus()).isEqualTo(202);
        String approvalId = staff.body(asked).get("approvalId").asText();
        assertThat(jdbc.queryForObject("select status from payout_requests where id = ?", String.class, payout)).isEqualTo("PENDING");
        assertThat(postJson(finance.session(), "/api/v1/admin/payouts/" + payout + "/mark-paid", Map.of("paymentReference", "UTR2"))
                .getStatus()).as("asked once").isEqualTo(409);
        // Finance cannot approve her own (she does not even hold payouts.approve); the manager can.
        assertThat(approve(finance, approvalId).getStatus()).isEqualTo(403);
        MockHttpServletResponse done = approve(manager, approvalId);
        assertThat(staff.body(done).get("status").asText()).as(done.getContentAsString()).isEqualTo("EXECUTED");
        assertThat(jdbc.queryForMap("select status, payment_reference, paid_by from payout_requests where id = ?", payout))
                .containsEntry("status", "PAID").containsEntry("payment_reference", "UTR000000000001")
                .containsEntry("paid_by", manager.accountId());
    }

    @Test
    void nobodyApprovesTheirOwnRequestUnlessTheyAreTheOnlyOwner() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member second = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member manager = staff.join(StaffRole.MANAGER);

        MockHttpServletResponse asked = postJson(owner.session(), "/api/v1/admin/staff/" + manager.staffId() + "/role",
                Map.of("role", "SUPPORT_AGENT"));
        assertThat(asked.getStatus()).isEqualTo(202);
        String approvalId = staff.body(asked).get("approvalId").asText();
        MockHttpServletResponse own = approve(owner, approvalId);
        assertThat(own.getStatus()).isEqualTo(409);
        assertThat(staff.body(own).get("error").asText()).isEqualTo("OWN_REQUEST");

        // The only active owner may - marked, recorded, alerted. Every other
        // active owner on this machine is set aside for the moment, and put back.
        List<UUID> others = jdbc.queryForList("select id from staff_members where role = 'OWNER' and status = 'ACTIVE' and id <> ?",
                UUID.class, owner.staffId());
        try {
            jdbc.update("update staff_members set status = 'DISABLED' where id = any(?)", (Object) others.toArray(new UUID[0]));
            MockHttpServletResponse self = approve(owner, approvalId);
            assertThat(self.getStatus()).as(self.getContentAsString()).isEqualTo(200);
            assertThat(staff.body(self).get("selfApproved").asBoolean()).isTrue();
        } finally {
            jdbc.update("update staff_members set status = 'ACTIVE' where id = any(?)", (Object) others.toArray(new UUID[0]));
        }
        assertThat(jdbc.queryForObject("select self_approved from staff_approval_requests where id = ?::uuid", Boolean.class, approvalId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from staff_audit_events where action = 'alert.approval.self'"
                + " and target_id in (select seq::text from staff_audit_events where action = 'approval.self' and target_id = ?)",
                Integer.class, approvalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select role from staff_members where id = ?", String.class, manager.staffId())).isEqualTo("SUPPORT_AGENT");
        assertThat(second.staffId()).isNotNull();
    }

    @Test
    void anExportIsApprovedOnceAndDownloadedOnceByWhoeverAsked() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member second = staff.join(StaffRole.OWNER);
        String path = "/api/v1/admin/audit/export.csv?action=staff.";

        MockHttpServletResponse straight = mvc.perform(owner.session().on(get(path))).andReturn().getResponse();
        assertThat(staff.body(straight).get("error").asText()).isEqualTo("APPROVAL_REQUIRED");

        MockHttpServletResponse asked = postJson(owner.session(), "/api/v1/admin/approvals/exports",
                Map.of("path", path, "reason", "Quarterly review of staff changes"));
        assertThat(asked.getStatus()).isEqualTo(202);
        String approvalId = staff.body(asked).get("approvalId").asText();
        assertThat(staff.body(approve(second, approvalId)).get("status").asText()).isEqualTo("APPROVED");

        // Someone else, with the same approval: no.
        assertThat(mvc.perform(second.session().on(get(path + "&approval=" + approvalId))).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        // A different download, with this approval: no.
        assertThat(mvc.perform(owner.session().on(get("/api/v1/admin/audit/export.csv?approval=" + approvalId)))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        MockHttpServletResponse file = mvc.perform(owner.session().on(get(path + "&approval=" + approvalId))).andReturn().getResponse();
        assertThat(file.getStatus()).isEqualTo(200);
        assertThat(file.getContentAsString()).startsWith("seq,at,staff");
        assertThat(mvc.perform(owner.session().on(get(path + "&approval=" + approvalId))).andReturn().getResponse().getStatus())
                .as("once").isEqualTo(403);
    }

    @Test
    void aRequestLeftTooLongExpiresAndAManagerInviteNeedsAnOwner() throws Exception {
        StaffTestSupport.Member owner = staff.join(StaffRole.OWNER);
        StaffTestSupport.Member second = staff.join(StaffRole.OWNER);
        String email = staff.newEmail("newmgr");
        MockHttpServletResponse asked = postJson(owner.session(), "/api/v1/admin/staff/invites",
                Map.of("email", email, "displayName", "New manager", "role", "MANAGER"));
        assertThat(asked.getStatus()).isEqualTo(202);
        String approvalId = staff.body(asked).get("approvalId").asText();
        assertThat(jdbc.queryForObject("select count(*) from staff_invites where lower(email) = lower(?)", Integer.class, email)).isZero();

        jdbc.update("update staff_approval_requests set expires_at = now() - interval '1 minute' where id = ?::uuid", approvalId);
        MockHttpServletResponse late = approve(second, approvalId);
        assertThat(late.getStatus()).isEqualTo(409);
        assertThat(staff.body(late).get("message").asText()).contains("expired");

        MockHttpServletResponse again = postJson(owner.session(), "/api/v1/admin/staff/invites",
                Map.of("email", email, "displayName", "New manager", "role", "MANAGER"));
        JsonNode done = staff.body(approve(second, staff.body(again).get("approvalId").asText()));
        assertThat(done.get("status").asText()).isEqualTo("EXECUTED");
        assertThat(done.get("oneTimeValue").asText()).contains("#invite=");
        assertThat(jdbc.queryForObject("select role from staff_invites where lower(email) = lower(?) and status = 'PENDING'",
                String.class, email)).isEqualTo("MANAGER");
    }
}
