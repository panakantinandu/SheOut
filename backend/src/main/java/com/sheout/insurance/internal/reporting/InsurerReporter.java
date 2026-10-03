package com.sheout.insurance.internal.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The swap point for declaring covered trips to the insurer - a bordereau,
 * the list of trips a master policy covered on a day. The same shape as
 * OtpSender and DocumentStorage: the flow depends on this, the transport is
 * configuration.
 * <p>
 * The default, CsvBordereauReporter, produces the day's file for an operator
 * to download from the console and send. ApiInsurerReporter is a stub that
 * refuses: no insurer's API is integrated or guessed at.
 */
public interface InsurerReporter {

    String name();

    /**
     * Declares one day's trips. delivered says whether this reporter sent it
     * itself (an API) or produced a file a person must send (the CSV).
     * Throws when it could not report; the trips are then marked FAILED.
     */
    Report report(LocalDate day, List<Line> lines);

    record Report(byte[] file, String contentType, String fileName, boolean delivered) {
    }

    /** One covered trip, in the terms a bordereau uses. Times are as they happened; the reporter formats them. */
    record Line(UUID bookingId, String insurerName, String policyNumber, Instant startedAt, Instant endedAt,
                String category, String pickupArea, String dropArea, BigDecimal premium) {
    }
}
