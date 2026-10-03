package com.sheout.platform;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every migration, two ways, in throwaway schemas of the local database:
 * from empty to the latest, and from production's shape today (V54) with the
 * data V55-V60 change - an RC photo and a police check recorded without
 * evidence - to the latest. V61 (staff accounts) is also run over a database
 * holding a phone-login ADMIN account, as production's does.
 * <p>
 * Plain JDBC and Flyway, no Spring context: this is about the SQL. Needs the
 * local Postgres (DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD, defaulted
 * like application-local.yml).
 */
class MigrationPathTest {

    private final String schema = "mig_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:postgresql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "5432") + "/" + env("DB_NAME", "sheout"),
            env("DB_USERNAME", "sheout"), env("DB_PASSWORD", "sheout"));
    private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    @AfterEach
    void dropSchema() {
        jdbc.execute("drop schema if exists " + schema + " cascade");
    }

    @Test
    void fromEmptyToTheLatest() {
        var result = flyway(null).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("61");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name in"
                        + " ('partner_documents','police_verifications','insurance_policies','trip_coverages','tax_invoices')",
                Integer.class, schema)).isEqualTo(5);
    }

    @Test
    void fromProductionsShapeTodayWithTheDataTheNewMigrationsChange() {
        assertThat(flyway("54").migrate().targetSchemaVersion).isEqualTo("54");
        UUID partnerWithRc = UUID.randomUUID();
        UUID riderNoRc = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".verification_records (id, account_id, role, gender_verification_status,"
                + " police_verification_status, rc_document_key, document_submitted_at, created_at, updated_at)"
                + " values (gen_random_uuid(), ?, 'DRIVER', 'VERIFIED', 'VERIFIED', 'rc-key-1', now(), now(), now())", partnerWithRc);
        jdbc.update("insert into " + schema + ".verification_records (id, account_id, role, gender_verification_status,"
                + " created_at, updated_at) values (gen_random_uuid(), ?, 'CUSTOMER', 'VERIFIED', now(), now())", riderNoRc);

        var result = flyway(null).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("61");
        // V55: her RC photo is a document of its own now, waiting for an operator to date it.
        assertThat(jdbc.queryForMap("select type, status, document_key from " + schema + ".partner_documents where account_id = ?",
                partnerWithRc))
                .containsEntry("type", "VEHICLE_RC").containsEntry("status", "UNDER_REVIEW").containsEntry("document_key", "rc-key-1");
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".partner_documents where account_id = ?",
                Integer.class, riderNoRc)).isZero();
        // V56: a police check recorded without evidence falls due the day this deploys.
        assertThat(jdbc.queryForObject("select police_reverify_due_on = current_date from " + schema
                + ".verification_records where account_id = ?", Boolean.class, partnerWithRc)).isTrue();
        assertThat(jdbc.queryForObject("select police_reverify_due_on from " + schema
                + ".verification_records where account_id = ?", java.sql.Date.class, riderNoRc)).isNull();
        // V58/V59: new columns are there and empty on old rows.
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = ?"
                + " and ((table_name = 'bookings' and column_name = 'fare_base_fare')"
                + " or (table_name = 'payments' and column_name = 'tax_amount'))", Integer.class, schema)).isEqualTo(2);
        // V60: the old column is gone, and her RC was not copied twice.
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema = ?"
                + " and table_name = 'verification_records' and column_name = 'rc_document_key'", Integer.class, schema)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".partner_documents where account_id = ?",
                Integer.class, partnerWithRc)).isEqualTo(1);
    }

    @Test
    void anRcTheOldReleaseWroteAfterV55IsKeptBeforeTheColumnGoes() {
        assertThat(flyway("59").migrate().targetSchemaVersion).isEqualTo("59");
        UUID noChecklistRc = UUID.randomUUID();
        UUID alreadyHasOne = UUID.randomUUID();
        UUID alreadyCopied = UUID.randomUUID();
        for (Object[] row : new Object[][] {{noChecklistRc, "late-rc-1"}, {alreadyHasOne, "late-rc-2"}, {alreadyCopied, "same-rc"}}) {
            jdbc.update("insert into " + schema + ".verification_records (id, account_id, role, gender_verification_status,"
                    + " police_verification_status, rc_document_key, document_submitted_at, created_at, updated_at)"
                    + " values (gen_random_uuid(), ?, 'DRIVER', 'VERIFIED', 'VERIFIED', ?, now(), now(), now())", row);
        }
        String currentRc = "insert into " + schema + ".partner_documents (id, account_id, type, document_key, status, source,"
                + " created_at, updated_at) values (gen_random_uuid(), ?, 'VEHICLE_RC', ?, 'VERIFIED', 'PARTNER_UPLOAD', now(), now())";
        jdbc.update(currentRc, alreadyHasOne, "checklist-rc");
        jdbc.update(currentRc, alreadyCopied, "same-rc");

        assertThat(flyway(null).migrate().targetSchemaVersion).isEqualTo("61");

        // Nothing on her checklist: the late photo becomes her RC, waiting for review.
        assertThat(jdbc.queryForMap("select document_key, status, superseded_at from " + schema
                + ".partner_documents where account_id = ?", noChecklistRc))
                .containsEntry("document_key", "late-rc-1").containsEntry("status", "UNDER_REVIEW").containsEntry("superseded_at", null);
        // She already has a current RC: it stays current; the late one is kept as history.
        assertThat(jdbc.queryForObject("select document_key from " + schema
                + ".partner_documents where account_id = ? and superseded_at is null", String.class, alreadyHasOne)).isEqualTo("checklist-rc");
        assertThat(jdbc.queryForObject("select count(*) from " + schema
                + ".partner_documents where account_id = ? and document_key = 'late-rc-2' and superseded_at is not null",
                Integer.class, alreadyHasOne)).isEqualTo(1);
        // Already copied: not copied again.
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".partner_documents where account_id = ?",
                Integer.class, alreadyCopied)).isEqualTo(1);
    }

    @Test
    void staffTablesArriveBesideTheOldAdminAccountWithoutTouchingIt() {
        assertThat(flyway("60").migrate().targetSchemaVersion).isEqualTo("60");
        UUID legacyAdmin = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".accounts (id, phone_number, role, created_at, updated_at)"
                + " values (?, '+910000000000', 'ADMIN', now(), now())", legacyAdmin);

        var result = flyway(null).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("61");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name in"
                        + " ('staff_members','staff_invites','staff_recovery_codes','staff_sessions')",
                Integer.class, schema)).isEqualTo(4);
        // The old account is left exactly as it was: the first OWNER invitation
        // links to it later (OwnerBootstrap); no SQL guesses who it belongs to.
        assertThat(jdbc.queryForMap("select phone_number, role from " + schema + ".accounts where id = ?", legacyAdmin))
                .containsEntry("phone_number", "+910000000000").containsEntry("role", "ADMIN");
        // An auditor without an end date is refused by the database itself.
        UUID staffAccount = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".accounts (id, role, created_at, updated_at) values (?, 'ADMIN', now(), now())",
                staffAccount);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("insert into " + schema + ".staff_members"
                        + " (id, account_id, email, display_name, role, status, password_hash, password_changed_at, totp_secret,"
                        + " created_at, updated_at) values (gen_random_uuid(), ?, 'ca@example.com', 'CA', 'AUDITOR', 'ACTIVE',"
                        + " 'x', now(), 'y', now(), now())", staffAccount))
                .hasMessageContaining("chk_staff_auditor_expires");
    }
}
