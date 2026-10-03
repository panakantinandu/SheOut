package com.sheout.payments.internal.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GST is off unless every rate, SAC code and the GSTIN are set; the tax is
 * found inside what she paid and adds back to it exactly; and the financial
 * year turns on 1 April.
 */
class TaxRulesTest {

    static TaxConfiguration config(boolean enabled, String gstin, String rate, boolean inclusive) {
        return new TaxConfiguration(enabled, gstin, "SheOut Mobility Private Limited", inclusive, "Telangana (36)", "SO",
                rate, rate, rate, rate, rate, rate, "996412", "996412", "996412", "996812", "998599", "998599");
    }

    @Test
    void offByDefaultAndNeedsNothingConfigured() {
        TaxConfiguration off = new TaxConfiguration(false, "", "", true, "Telangana (36)", "SO",
                "", "", "", "", "", "", "", "", "", "", "", "");

        assertThat(off.enabled()).isFalse();
    }

    @Test
    void switchedOnHalfConfiguredItRefusesToStartAndSaysWhat() {
        assertThatThrownBy(() -> config(true, "", "5", true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SHEOUT_GSTIN");
        assertThatThrownBy(() -> new TaxConfiguration(true, "36ABCDE1234F1Z5", "SheOut", true, "Telangana (36)", "SO",
                "5", "", "5", "5", "18", "18", "996412", "996412", "996412", "996812", "998599", ""))
                .hasMessageContaining("GST_RATE_AUTO is missing")
                .hasMessageContaining("GST_SAC_SELLER_LISTING_FEE is missing");
        assertThatThrownBy(() -> config(true, "36ABCDE1234F1Z5", "five", true)).hasMessageContaining("not a number");
    }

    @Test
    void taxOnTopIsRefusedBecauseItWouldChangeWhatRidersPay() {
        assertThatThrownBy(() -> config(true, "36ABCDE1234F1Z5", "5", false))
                .hasMessageContaining("FARES_TAX_INCLUSIVE=false is not supported");
    }

    @Test
    void fullyConfiguredItStarts() {
        TaxConfiguration on = config(true, "36abcde1234f1z5", "5", true);

        assertThat(on.enabled()).isTrue();
        assertThat(on.gstin()).isEqualTo("36ABCDE1234F1Z5");
        assertThat(on.rate(TaxCategory.BIKE)).isEqualByComparingTo("5");
    }

    @Test
    void theTaxIsFoundInsideTheFareAndAddsBackToItExactly() {
        GstService.Split split = GstService.splitInclusive(new BigDecimal("100.00"), new BigDecimal("5"));

        assertThat(split.taxableValue()).isEqualByComparingTo("95.24");
        assertThat(split.tax()).isEqualByComparingTo("4.76");
        assertThat(split.cgst()).isEqualByComparingTo("2.38");
        assertThat(split.sgst()).isEqualByComparingTo("2.38");
        for (String fare : new String[] {"37.00", "41.55", "123.45", "1.00"}) {
            GstService.Split s = GstService.splitInclusive(new BigDecimal(fare), new BigDecimal("18"));
            assertThat(s.taxableValue().add(s.tax())).as(fare).isEqualByComparingTo(fare);
            assertThat(s.cgst().add(s.sgst())).as(fare).isEqualByComparingTo(s.tax());
        }
    }

    @Test
    void theFinancialYearTurnsOnTheFirstOfApril() {
        assertThat(GstService.financialYear(LocalDate.of(2026, 3, 31))).isEqualTo("2025-26");
        assertThat(GstService.financialYear(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(GstService.financialYear(LocalDate.of(2027, 1, 15))).isEqualTo("2026-27");
        assertThat(GstService.financialYear(LocalDate.of(2099, 6, 1))).isEqualTo("2099-00");
    }

    @Test
    void theInvoiceIsAReadablePdf() {
        byte[] pdf = SimplePdfInvoiceRenderer.pdf(java.util.List.of("TAX INVOICE", "Billed to: Lakshmi (QA)", "Total: Rs 100.00"));
        String text = new String(pdf, java.nio.charset.StandardCharsets.US_ASCII);

        assertThat(text).startsWith("%PDF-1.4").endsWith("%%EOF\n").contains("(Billed to: Lakshmi \\(QA\\)) Tj");
        int xref = Integer.parseInt(text.substring(text.indexOf("startxref\n") + 10, text.indexOf("\n%%EOF")));
        assertThat(text.substring(xref)).startsWith("xref");
    }
}
