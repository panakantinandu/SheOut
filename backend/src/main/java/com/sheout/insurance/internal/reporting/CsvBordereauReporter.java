package com.sheout.insurance.internal.reporting;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The day's covered trips as a CSV, for an operator to download from the
 * console and send to the insurer or broker. It sends nothing itself.
 * <p>
 * The columns are what the brief asked for and what a bordereau commonly
 * carries; the insurer may want a different layout, which is a change to
 * this class only. Times are in India, as dates on a policy are.
 */
@Component
@ConditionalOnProperty(name = "sheout.insurance.reporter", havingValue = "csv", matchIfMissing = true)
public class CsvBordereauReporter implements InsurerReporter {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(INDIA);
    static final String HEADER = "booking_id,insurer,policy_number,coverage_started_at_ist,coverage_ended_at_ist,"
            + "category,pickup_area,drop_area,premium_inr";

    @Override
    public String name() {
        return "csv";
    }

    @Override
    public Report report(LocalDate day, List<Line> lines) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (Line line : lines) {
            csv.append(line.bookingId()).append(',')
                    .append(cell(line.insurerName())).append(',')
                    .append(cell(line.policyNumber())).append(',')
                    .append(time(line.startedAt())).append(',')
                    .append(time(line.endedAt())).append(',')
                    .append(cell(line.category())).append(',')
                    .append(cell(line.pickupArea())).append(',')
                    .append(cell(line.dropArea())).append(',')
                    .append(money(line.premium())).append("\r\n");
        }
        return new Report(csv.toString().getBytes(StandardCharsets.UTF_8), "text/csv; charset=utf-8",
                "sheout-bordereau-" + day + ".csv", false);
    }

    private static String time(Instant at) {
        return at == null ? "" : TIME.format(at);
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * RFC 4180 quoting, and a leading =, +, - or @ neutralised: a place name
     * typed by a rider ends up in a spreadsheet, and a spreadsheet runs
     * formulas.
     */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
