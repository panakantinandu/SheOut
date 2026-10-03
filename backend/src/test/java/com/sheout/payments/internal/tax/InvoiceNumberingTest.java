package com.sheout.payments.internal.tax;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invoice numbers run without gaps within a financial year, on the real
 * database: a capture that rolls back gives its number back. Run in a
 * far-future year so it never meets a real invoice. Needs local Postgres.
 */
@SpringBootTest(properties = {"sheout.warm-up.enabled=false", "spring.datasource.hikari.maximum-pool-size=3"})
@ActiveProfiles("local")
class InvoiceNumberingTest {

    private static final String YEAR = "2098-99";

    @Autowired TaxInvoiceRepository invoices;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    private GstService service() {
        return new GstService(TaxRulesTest.config(true, "36ABCDE1234F1Z5", "5", true), invoices,
                Clock.fixed(Instant.parse("2098-06-01T06:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from tax_invoices where financial_year = ?", YEAR);
        jdbc.update("delete from tax_invoice_sequences where financial_year = ?", YEAR);
    }

    private String issue(GstService service, UUID paymentId) {
        return transactions.execute(status -> service.applyOnCapture(paymentId, UUID.randomUUID(), new BigDecimal("96.00"),
                TaxCategory.BIKE, UUID.randomUUID(), "Lakshmi", "Passenger transport - bike taxi").orElseThrow()
                .invoice().getInvoiceNumber());
    }

    @Test
    void numbersRunWithoutGapsAndARolledBackCaptureGivesItsNumberBack() {
        GstService service = service();
        List<String> numbers = new ArrayList<>();
        numbers.add(issue(service, UUID.randomUUID()));
        numbers.add(issue(service, UUID.randomUUID()));

        // A capture that fails after taking its number.
        try {
            transactions.executeWithoutResult(status -> {
                service.applyOnCapture(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("50.00"), TaxCategory.BIKE,
                        UUID.randomUUID(), "X", "Passenger transport");
                throw new IllegalStateException("capture failed after the invoice");
            });
        } catch (IllegalStateException expected) {
            // rolled back
        }
        numbers.add(issue(service, UUID.randomUUID()));

        assertThat(numbers).containsExactly("SO/2098-99/000001", "SO/2098-99/000002", "SO/2098-99/000003");
    }

    @Test
    void onePaymentGetsOneInvoiceHoweverOftenCaptureRuns() {
        GstService service = service();
        UUID payment = UUID.randomUUID();

        String first = issue(service, payment);
        String again = issue(service, payment);

        assertThat(again).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from tax_invoices where financial_year = ?", Integer.class, YEAR))
                .isEqualTo(1);
    }

    @Test
    void withGstOffNothingIsIssuedAndNoNumberIsTaken() {
        GstService off = new GstService(new TaxConfiguration(false, "", "", true, "Telangana (36)", "SO",
                "", "", "", "", "", "", "", "", "", "", "", ""), invoices,
                Clock.fixed(Instant.parse("2098-06-01T06:00:00Z"), ZoneOffset.UTC));

        var applied = transactions.execute(status -> off.applyOnCapture(UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("96.00"), TaxCategory.BIKE, UUID.randomUUID(), "Lakshmi", "x"));

        assertThat(applied).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from tax_invoice_sequences where financial_year = ?", Integer.class, YEAR))
                .isZero();
    }
}
