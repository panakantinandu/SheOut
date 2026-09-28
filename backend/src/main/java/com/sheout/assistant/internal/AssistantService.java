package com.sheout.assistant.internal;

import com.sheout.assistant.AssistantAdminApi;
import com.sheout.auth.AccountRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One message to SheOut Help, in this order:
 * <ol>
 *   <li>Does it sound like an emergency? Then SOS and 112, now - no model
 *       call, no cap, no key needed.</li>
 *   <li>Within today's cap (per account, and for SheOut as a whole)?</li>
 *   <li>Ask the model, grounded in SheOut's content only.</li>
 *   <li>Record what it used, for the cap and the console's cost view.</li>
 * </ol>
 * Anything the model cannot answer - or a failed call - becomes a hand-off
 * to a person, never a guess or a dead end.
 */
@Service
public class AssistantService implements AssistantAdminApi {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final EmergencyDetector emergencies;
    private final KnowledgeBase knowledge;
    private final HelpModel model;
    private final AssistantUsageRepository usage;
    private final String modelName;
    private final int dailyLimit;
    private final int globalDailyLimit;
    private final BigDecimal inputPrice;
    private final BigDecimal outputPrice;
    private final BigDecimal cacheReadPrice;
    private final BigDecimal cacheWritePrice;

    AssistantService(EmergencyDetector emergencies, KnowledgeBase knowledge, HelpModel model, AssistantUsageRepository usage,
                     @Value("${sheout.assistant.model:claude-opus-5}") String modelName,
                     // Far above what someone with a real question sends in a day; low enough that nobody can run up a bill.
                     @Value("${sheout.assistant.daily-limit-per-account:30}") int dailyLimit,
                     @Value("${sheout.assistant.global-daily-limit:3000}") int globalDailyLimit,
                     // US dollars per million tokens, for the console's estimate. Defaults are Claude Opus 5's.
                     @Value("${sheout.assistant.price.input-per-mtok:5}") BigDecimal inputPrice,
                     @Value("${sheout.assistant.price.output-per-mtok:25}") BigDecimal outputPrice,
                     @Value("${sheout.assistant.price.cache-read-per-mtok:0.5}") BigDecimal cacheReadPrice,
                     @Value("${sheout.assistant.price.cache-write-per-mtok:6.25}") BigDecimal cacheWritePrice) {
        this.emergencies = emergencies;
        this.knowledge = knowledge;
        this.model = model;
        this.usage = usage;
        this.modelName = modelName;
        this.dailyLimit = dailyLimit;
        this.globalDailyLimit = globalDailyLimit;
        this.inputPrice = inputPrice;
        this.outputPrice = outputPrice;
        this.cacheReadPrice = cacheReadPrice;
        this.cacheWritePrice = cacheWritePrice;
    }

    public enum Kind { ANSWER, ESCALATE, EMERGENCY, LIMIT_REACHED, UNAVAILABLE }

    /** A ticket the user can send to a person, pre-filled - she reviews it in Raise an issue before it goes. */
    public record TicketDraft(String category, String subject, String summary) {
    }

    public record Reply(Kind kind, String text, TicketDraft ticket, int remainingToday) {
    }

    public record Turn(boolean fromUser, String text) {
    }

    public Reply ask(UUID accountId, AccountRole role, String language, List<Turn> turns) {
        String last = turns.get(turns.size() - 1).text();
        LocalDate today = LocalDate.now(INDIA);

        if (emergencies.soundsLikeEmergency(last)) {
            usage.add(accountId, today, 0, 0, 0, 0, 0, 0, 1);
            log.warn("Help assistant: a message from {} sounded like an emergency - sent to SOS", accountId);
            return new Reply(Kind.EMERGENCY, null, null, remaining(accountId, today));
        }
        long usedToday = usage.messagesOn(accountId, today);
        if (usedToday >= dailyLimit || usage.allMessagesOn(today) >= globalDailyLimit) {
            return new Reply(Kind.LIMIT_REACHED, null, handOff(last), 0);
        }
        if (!model.available()) {
            return new Reply(Kind.UNAVAILABLE, null, handOff(last), remaining(accountId, today));
        }

        List<HelpModel.Turn> conversation = turns.stream().map(t -> new HelpModel.Turn(t.fromUser(), t.text())).toList();
        Optional<HelpModel.Result> result = model.answer(knowledge.systemPrompt(role), KnowledgeBase.languageInstruction(language), conversation);
        HelpModel.Usage spent = result.map(HelpModel.Result::usage).orElse(new HelpModel.Usage(0, 0, 0, 0));
        HelpModel.Decision decision = result.map(HelpModel.Result::decision).orElse(null);

        Reply reply;
        if (decision == null || decision.kind() == null || decision.reply() == null || decision.reply().isBlank()) {
            reply = new Reply(Kind.ESCALATE, null, handOff(last), 0);
        } else if (decision.kind() == HelpModel.Kind.EMERGENCY) {
            reply = new Reply(Kind.EMERGENCY, decision.reply(), null, 0);
        } else if (decision.kind() == HelpModel.Kind.ESCALATE) {
            reply = new Reply(Kind.ESCALATE, decision.reply(), new TicketDraft(
                    decision.ticketCategory() == null ? "OTHER" : decision.ticketCategory().name(),
                    clip(blankToNull(decision.ticketSubject()), 120, "Question from SheOut Help"),
                    clip(blankToNull(decision.ticketSummary()), 1500, last)), 0);
        } else {
            reply = new Reply(Kind.ANSWER, decision.reply(), null, 0);
        }
        usage.add(accountId, today, 1, spent.inputTokens(), spent.outputTokens(), spent.cacheReadTokens(), spent.cacheWriteTokens(),
                reply.kind() == Kind.ESCALATE ? 1 : 0, reply.kind() == Kind.EMERGENCY ? 1 : 0);
        return new Reply(reply.kind(), reply.text(), reply.ticket(), remaining(accountId, today));
    }

    /** When the assistant cannot help at all: what she asked, ready for a person. */
    private static TicketDraft handOff(String question) {
        return new TicketDraft("OTHER", "Question from SheOut Help", clip(question, 1500, question));
    }

    private int remaining(UUID accountId, LocalDate today) {
        return (int) Math.max(0, dailyLimit - usage.messagesOn(accountId, today));
    }

    // ------------------------------------------------------------ the console

    @Override
    public Usage usage(int days) {
        LocalDate since = LocalDate.now(INDIA).minusDays(Math.max(1, Math.min(days, 90)) - 1L);
        List<Day> rows = usage.dailyTotalsSince(since).stream().map(d -> new Day(d.getDay(), d.getAccounts(), d.getMessages(),
                d.getInputTokens(), d.getOutputTokens(), d.getCacheReadTokens(), d.getCacheWriteTokens(), d.getEscalations(),
                d.getEmergencies(), cost(d))).toList();
        return new Usage(modelName, dailyLimit, globalDailyLimit, model.available(), rows);
    }

    private BigDecimal cost(AssistantUsageRepository.DailyTotals d) {
        BigDecimal million = BigDecimal.valueOf(1_000_000);
        return BigDecimal.valueOf(d.getInputTokens()).multiply(inputPrice)
                .add(BigDecimal.valueOf(d.getOutputTokens()).multiply(outputPrice))
                .add(BigDecimal.valueOf(d.getCacheReadTokens()).multiply(cacheReadPrice))
                .add(BigDecimal.valueOf(d.getCacheWriteTokens()).multiply(cacheWritePrice))
                .divide(million, 4, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String clip(String s, int max, String fallback) {
        String value = s == null ? fallback : s;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
