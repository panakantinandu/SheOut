package com.sheout.staff.internal;

import com.sheout.staff.StaffAudit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The audit log cannot be edited through the database's front door, and an
 * edit made by going round it (dropping the trigger, as the table's owner
 * could) is caught by the chain - at the row that was changed.
 * <p>
 * Runs on the shared local database, so the tampering is undone in finally:
 * the chain must be intact again when this ends.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=4",
        "sheout.admin.phone-login-enabled=false", "sheout.staff.login.per-address=100000"})
@ActiveProfiles("local")
class StaffAuditChainTest {

    @Autowired StaffAudit audit;
    @Autowired StaffAuditWriter writer;
    @Autowired StaffAuditChainCheck check;
    @Autowired JdbcTemplate jdbc;

    private long append(String marker) {
        audit.record(new StaffAudit.Entry("test.chain", null, StaffAudit.Result.OK, "TEST", marker, "a reason | with a pipe", "{\"n\":1}"));
        return jdbc.queryForObject("select seq from staff_audit_events where target_id = ?", Long.class, marker);
    }

    @Test
    void rowsCannotBeChangedOrDeletedNormally() {
        long seq = append(UUID.randomUUID().toString());
        assertThatThrownBy(() -> jdbc.update("update staff_audit_events set reason = 'changed' where seq = ?", seq))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from staff_audit_events where seq = ?", seq))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate staff_audit_events")).hasMessageContaining("append-only");
    }

    @Test
    void anEditMadeAroundTheTriggerIsFoundAtTheRowThatWasChanged() {
        long first = append(UUID.randomUUID().toString());
        long tampered = append(UUID.randomUUID().toString());
        append(UUID.randomUUID().toString());
        assertThat(writer.verify().intact()).as("intact before anything is touched").isTrue();

        String original = jdbc.queryForObject("select reason from staff_audit_events where seq = ?", String.class, tampered);
        jdbc.execute("alter table staff_audit_events disable trigger trg_staff_audit_no_update");
        try {
            jdbc.update("update staff_audit_events set reason = 'nothing to see here' where seq = ?", tampered);
            StaffAuditWriter.Verification broken = writer.verify();
            assertThat(broken.intact()).isFalse();
            assertThat(broken.brokenAt()).isEqualTo(tampered);
            assertThat(broken.problem()).contains("no longer matches");

            // The nightly check says so loudly, in the log itself, for the owners.
            assertThat(check.check().intact()).isFalse();
            assertThat(jdbc.queryForObject("select count(*) from staff_audit_events where action = 'audit.chain.broken'"
                    + " and target_id = ?", Integer.class, String.valueOf(tampered))).isPositive();
        } finally {
            jdbc.update("update staff_audit_events set reason = ? where seq = ?", original, tampered);
            jdbc.execute("alter table staff_audit_events enable trigger trg_staff_audit_no_update");
        }
        assertThat(writer.verify().intact()).as("restored").isTrue();
        assertThat(first).isLessThan(tampered);
    }
}
