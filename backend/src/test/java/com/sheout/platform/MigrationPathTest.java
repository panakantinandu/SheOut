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
 * evidence - to the latest. V61 (staff accounts) is also run over a database
 * holding a phone-login ADMIN account, as production's does. (V60, dropping
 * the old RC column, ships in the release after V55-V59 is live.)
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
        assertThat(result.targetSchemaVersion).isEqualTo("72");
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
        assertThat(result.targetSchemaVersion).isEqualTo("72");
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

    @Test
    void sellersMoveToMonthlyPlansWithAFreeMonthAndTheirOldFeeKept() {
        assertThat(flyway("64").migrate().targetSchemaVersion).isEqualTo("64");
        UUID live = UUID.randomUUID();
        UUID waitingToPay = UUID.randomUUID();
        UUID draft = UUID.randomUUID();
        UUID liveAccount = UUID.randomUUID();
        String insertSeller = "insert into " + schema + ".seller_profiles (id, account_id, business_name, category,"
                + " contact_phone, status, activated_at, created_at, updated_at) values (?, ?, 'Shop', 'GIFTS', '9000000000', ?, ?, now(), now())";
        jdbc.update(insertSeller, live, liveAccount, "ACTIVE", java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(86400 * 40)));
        jdbc.update(insertSeller, waitingToPay, UUID.randomUUID(), "APPROVED_AWAITING_PAYMENT", null);
        jdbc.update(insertSeller, draft, UUID.randomUUID(), "DRAFT", null);
        jdbc.update("insert into " + schema + ".payments (id, amount, method, status, created_at, updated_at, purpose,"
                + " payer_account_id, seller_id) values (gen_random_uuid(), 299, 'UPI', 'CAPTURED', now(), now(),"
                + " 'SELLER_LISTING_FEE', ?, ?)", liveAccount, live);

        var result = flyway(null).migrate();

        assertThat(result.success).isTrue();
        // A live shop and one approved but unpaid are both live on a free month from today.
        for (UUID seller : java.util.List.of(live, waitingToPay)) {
            assertThat(jdbc.queryForMap("select status, paid_periods, paid_until > now() + interval '27 days' as month_ahead,"
                    + " free_until = paid_until as free from " + schema + ".seller_profiles where id = ?", seller))
                    .containsEntry("status", "ACTIVE").containsEntry("paid_periods", 0)
                    .containsEntry("month_ahead", true).containsEntry("free", true);
        }
        assertThat(jdbc.queryForMap("select status, paid_until from " + schema + ".seller_profiles where id = ?", draft))
                .containsEntry("status", "DRAFT").containsEntry("paid_until", null);
        // The one-time fee she paid is kept, as period 0.
        assertThat(jdbc.queryForObject("select seller_period from " + schema + ".payments where seller_id = ?",
                Integer.class, live)).isZero();
        // A second fee row for the same seller is now allowed, for another month - but never the same month twice.
        String fee = "insert into " + schema + ".payments (id, amount, method, status, created_at, updated_at, purpose,"
                + " payer_account_id, seller_id, seller_period) values (gen_random_uuid(), 499, 'UPI', 'PENDING', now(), now(),"
                + " 'SELLER_LISTING_FEE', ?, ?, 1)";
        jdbc.update(fee, liveAccount, live);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(fee, liveAccount, live))
                .hasMessageContaining("uq_payments_seller_period");
    }

    @Test
    void staffTablesArriveBesideTheOldAdminAccountWithoutTouchingIt() {
        assertThat(flyway("59").migrate().targetSchemaVersion).isEqualTo("59");
        UUID legacyAdmin = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".accounts (id, phone_number, role, created_at, updated_at)"
                + " values (?, '+910000000000', 'ADMIN', now(), now())", legacyAdmin);

        var result = flyway(null).migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("72");
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
