package com.sheout.insurance.internal.reporting;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * A STUB. Where a real insurer or broker API integration would go, selected
 * by INSURANCE_REPORTER=api. It sends nothing and refuses every report, so
 * choosing it by mistake marks trips FAILED in the console rather than
 * quietly pretending they were declared.
 * <p>
 * Implementing it means writing against the insurer's real API documentation
 * - endpoint, authentication, payload, acknowledgement - none of which is
 * known yet and none of which is guessed at here.
 */
@Component
@ConditionalOnProperty(name = "sheout.insurance.reporter", havingValue = "api")
public class ApiInsurerReporter implements InsurerReporter {

    @Override
    public String name() {
        return "api (not integrated)";
    }

    @Override
    public Report report(LocalDate day, List<Line> lines) {
        throw new IllegalStateException("No insurer API is integrated; use INSURANCE_REPORTER=csv");
    }
}
