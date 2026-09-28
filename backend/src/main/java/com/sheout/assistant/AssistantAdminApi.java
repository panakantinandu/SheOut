package com.sheout.assistant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The console's view of the help assistant: use and what it cost, per day. */
public interface AssistantAdminApi {

    Usage usage(int days);

    record Usage(String model, int dailyLimitPerAccount, int globalDailyLimit, boolean available, List<Day> days) {
    }

    /** estimatedCostUsd is from the configured per-token prices - an estimate; the Anthropic Console has the bill. */
    record Day(LocalDate day, long accounts, long messages, long inputTokens, long outputTokens, long cacheReadTokens,
               long cacheWriteTokens, long escalations, long emergencies, BigDecimal estimatedCostUsd) {
    }
}
