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
 * One request per message, structured output (HelpModel.Decision), low
 * effort - these are short factual answers from a fixed text, not reasoning
 * problems. The large static part of the system prompt is marked for prompt
 * caching, so after the first question it is read from cache at a tenth of
 * the input price. The model is a setting (ASSISTANT_MODEL); a cheaper model
 * is the main cost lever and is SheOut's choice to make.
 * <p>
 * Server-side refusal fallbacks are on (the "default" routing), so a
 * request the model declines can be retried by another model instead of
 * ending the answer; a refusal that still comes back is handed to a person.
 */
@Component
class ClaudeHelpModel implements HelpModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeHelpModel.class);

    private final AnthropicClient client;
    private final String model;

    ClaudeHelpModel(@Value("${sheout.assistant.anthropic-api-key:}") String apiKey,
                    @Value("${sheout.assistant.model:claude-opus-5}") String model) {
        this.model = model;
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
        StructuredMessageCreateParams.Builder<Decision> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4000L)
                .systemOfBetaTextBlockParams(List.of(
                        // Stable across every request for this app: cached.
                        BetaTextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(BetaCacheControlEphemeral.builder().build())
                                .build(),
                        // Varies by user; after the cache breakpoint.
                        BetaTextBlockParam.builder().text(languageInstruction).build()))
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .outputConfig(Decision.class, BetaOutputConfig.Effort.LOW);
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
            log.warn("Help assistant: Anthropic rate limit - {}", e.getMessage());
            return Optional.empty();
        } catch (AnthropicServiceException e) {
            log.error("Help assistant: Anthropic API error {} - {}", e.statusCode(), e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            // Includes a structured answer that did not parse. Never let it reach the user as a 500.
            log.error("Help assistant: request failed", e);
            return Optional.empty();
        }
    }
}
