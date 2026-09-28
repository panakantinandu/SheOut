package com.sheout.assistant.internal;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlockParam;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The help assistant's model: Claude, through the official Java SDK.
 * <p>
 * One request per message, structured output (HelpModel.Decision), capped
 * at ASSISTANT_MAX_TOKENS (300): these are short factual answers from a
 * fixed text, not reasoning problems. Claude Haiku 4.5 by default.
 * <p>
 * The static part of the system prompt is marked for prompt caching. Haiku
 * 4.5 only caches a prefix of 4,096 tokens or more, and SheOut's help content
 * is shorter than that today, so on Haiku the marker is usually a no-op; on
 * a model with a lower minimum it saves most of the input cost.
 * <p>
 * Effort and server-side refusal fallbacks are sent only to models that take
 * them (Opus and Sonnet); Haiku 4.5 does not, and would reject the request.
 * A refusal that comes back is handed to a person.
 * <p>
 * Nothing the user wrote is logged. The one place it could reach a log is
 * an API error message that echoes the request, so those are logged with
 * phone numbers masked (PhoneMasking).
 */
@Component
class ClaudeHelpModel implements HelpModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeHelpModel.class);

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    ClaudeHelpModel(@Value("${sheout.assistant.anthropic-api-key:}") String apiKey,
                    @Value("${sheout.assistant.model:claude-haiku-4-5-20251001}") String model,
                    @Value("${sheout.assistant.max-tokens:300}") long maxTokens) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.client = apiKey == null || apiKey.isBlank()
                ? null
                : AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        // A person is waiting on the other end of every call.
                        .timeout(Duration.ofSeconds(45))
                        .maxRetries(1)
                        .build();
        log.info("Help assistant model: {} ({})", model, client == null ? "no API key - unavailable" : "configured");
    }

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
    public Optional<Result> answer(String systemPrompt, String languageInstruction, List<Turn> turns) {
        if (client == null) {
            return Optional.empty();
        }
        MessageCreateParams.Builder base = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .systemOfBetaTextBlockParams(List.of(
                        // Stable across every request for this app: cached.
                        BetaTextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(BetaCacheControlEphemeral.builder().build())
                                .build(),
                        // Varies by user; after the cache breakpoint.
                        BetaTextBlockParam.builder().text(languageInstruction).build()));
        StructuredMessageCreateParams.Builder<Decision> params;
        if (supportsEffortAndFallbacks(model)) {
            params = base
                    .addBeta("server-side-fallback-2026-07-01")
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    .outputConfig(Decision.class, BetaOutputConfig.Effort.LOW);
        } else {
            params = base.outputConfig(Decision.class);
        }
        for (Turn turn : turns) {
            if (turn.fromUser()) {
                params.addUserMessage(turn.text());
            } else {
                params.addAssistantMessage(turn.text());
            }
        }
        try {
            StructuredMessage<Decision> response = client.beta().messages().create(params.build());
            var usage = response.usage();
            Usage spent = new Usage(usage.inputTokens(), usage.outputTokens(),
                    usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
            if (response.stopReason().map(r -> r.equals(BetaStopReason.REFUSAL)).orElse(false)) {
                log.warn("Help assistant: the model declined to answer - handing to a person");
                return Optional.of(new Result(null, spent));
            }
            Optional<Decision> decision = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst();
            if (decision.isEmpty()) {
                log.warn("Help assistant: no structured answer (stop reason {})", response.stopReason().orElse(null));
            }
            return Optional.of(new Result(decision.orElse(null), spent));
        } catch (RateLimitException e) {
            log.warn("Help assistant: Anthropic rate limit - {}", PhoneMasking.mask(e.getMessage()));
            return Optional.empty();
        } catch (AnthropicServiceException e) {
            log.error("Help assistant: Anthropic API error {} - {}", e.statusCode(), PhoneMasking.mask(e.getMessage()));
            return Optional.empty();
        } catch (RuntimeException e) {
            // Includes a structured answer that did not parse, or one cut off at max tokens.
            // Never let it reach the user as a 500. The class only: its message can quote the reply.
            log.error("Help assistant: request failed ({}: {})", e.getClass().getSimpleName(), PhoneMasking.mask(e.getMessage()));
            return Optional.empty();
        }
    }

    /** Effort and server-side fallbacks: Opus and Sonnet take them; Haiku 4.5 rejects the request. */
    static boolean supportsEffortAndFallbacks(String model) {
        return model != null && !model.startsWith("claude-haiku");
    }
}
