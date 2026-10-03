package com.sheout.insurance.internal.reporting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The day's bordereau is a file an operator sends; it sends nothing, and a place name cannot become a formula. */
class CsvBordereauReporterTest {

    @Test
    void oneLinePerTripWithTimesInIndia() {
        UUID booking = UUID.randomUUID();
        var report = new CsvBordereauReporter().report(LocalDate.of(2026, 10, 3), List.of(new InsurerReporter.Line(
                booking, "Example General", "MP-1", Instant.parse("2026-10-03T13:30:00Z"),
                Instant.parse("2026-10-03T13:55:10Z"), "BIKE", "Banjara Hills, Hyderabad", "=HYPERLINK(\"x\")",
                new BigDecimal("1.5"))));

        String[] lines = new String(report.file(), StandardCharsets.UTF_8).split("\r\n");
        assertThat(report.delivered()).as("an operator sends it").isFalse();
        assertThat(report.fileName()).isEqualTo("sheout-bordereau-2026-10-03.csv");
        assertThat(lines[0]).isEqualTo(CsvBordereauReporter.HEADER);
        assertThat(lines[1]).startsWith(booking + ",Example General,MP-1,2026-10-03 19:00:00,2026-10-03 19:25:10,BIKE,")
                .contains("\"Banjara Hills, Hyderabad\"")
                .contains("\"'=HYPERLINK(\"\"x\"\")\"")
                .endsWith(",1.50");
    }

    @Test
    void theApiReporterIsAStubThatRefuses() {
        var api = new ApiInsurerReporter();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> api.report(LocalDate.now(), List.of()))
                .hasMessageContaining("No insurer API is integrated");
    }
}
