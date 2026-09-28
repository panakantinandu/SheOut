package com.sheout.assistant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface AssistantUsageRepository extends JpaRepository<AssistantUsageEntity, AssistantUsageEntity.Key> {

    /** Adds to the day's row, creating it - one statement, so two messages at once are both counted. */
    @Modifying
    @Transactional
    @Query(value = """
            insert into assistant_usage (account_id, day, messages, input_tokens, output_tokens, cache_read_tokens,
                                         cache_write_tokens, escalations, emergencies)
            values (:accountId, :day, :messages, :inputTokens, :outputTokens, :cacheRead, :cacheWrite, :escalations, :emergencies)
            on conflict (account_id, day) do update set
                messages = assistant_usage.messages + excluded.messages,
                input_tokens = assistant_usage.input_tokens + excluded.input_tokens,
                output_tokens = assistant_usage.output_tokens + excluded.output_tokens,
                cache_read_tokens = assistant_usage.cache_read_tokens + excluded.cache_read_tokens,
                cache_write_tokens = assistant_usage.cache_write_tokens + excluded.cache_write_tokens,
                escalations = assistant_usage.escalations + excluded.escalations,
                emergencies = assistant_usage.emergencies + excluded.emergencies
            """, nativeQuery = true)
    void add(@Param("accountId") UUID accountId, @Param("day") LocalDate day, @Param("messages") int messages,
             @Param("inputTokens") long inputTokens, @Param("outputTokens") long outputTokens,
             @Param("cacheRead") long cacheReadTokens, @Param("cacheWrite") long cacheWriteTokens,
             @Param("escalations") int escalations, @Param("emergencies") int emergencies);

    @Query(value = "select coalesce(sum(messages), 0) from assistant_usage where account_id = :accountId and day = :day", nativeQuery = true)
    long messagesOn(@Param("accountId") UUID accountId, @Param("day") LocalDate day);

    @Query(value = "select coalesce(sum(messages), 0) from assistant_usage where day = :day", nativeQuery = true)
    long allMessagesOn(@Param("day") LocalDate day);

    interface DailyTotals {
        LocalDate getDay();
        long getAccounts();
        long getMessages();
        long getInputTokens();
        long getOutputTokens();
        long getCacheReadTokens();
        long getCacheWriteTokens();
        long getEscalations();
        long getEmergencies();
    }

    @Query(value = """
            select day as "day", count(*) as "accounts", sum(messages) as "messages", sum(input_tokens) as "inputTokens",
                   sum(output_tokens) as "outputTokens", sum(cache_read_tokens) as "cacheReadTokens",
                   sum(cache_write_tokens) as "cacheWriteTokens", sum(escalations) as "escalations", sum(emergencies) as "emergencies"
            from assistant_usage where day >= :since group by day order by day desc
            """, nativeQuery = true)
    List<DailyTotals> dailyTotalsSince(@Param("since") LocalDate since);
}
