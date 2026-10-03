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
 * data V55-V59 change - an RC photo and a police check recorded without
 * evidence - to the latest.
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
        assertThat(result.targetSchemaVersion).isEqualTo("59");
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
        assertThat(result.targetSchemaVersion).isEqualTo("59");
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
    }
}
