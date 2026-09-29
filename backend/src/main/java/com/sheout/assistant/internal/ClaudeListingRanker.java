package com.sheout.assistant.internal;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Marketplace search "in your own words": Claude picks listings from the
 * ones it is given, as structured output (ListingRankerModel.Picks).
 * <p>
 * The same key and model as SheOut Help (ClaudeHelpModel), but its own
 * client: a search has to feel like a search, so it waits at most
 * ASSISTANT_SEARCH_TIMEOUT_SECONDS (8) and never retries - a slow answer
 * falls back to keyword search instead of keeping her waiting. Output is
 * capped at ASSISTANT_SEARCH_MAX_TOKENS (200): a list of references.
 * <p>
 * Nothing the shopper typed is logged; API errors are logged with phone
 * numbers masked, as for the help assistant.
 */
@Component
class ClaudeListingRanker implements ListingRankerModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeListingRanker.class);

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    ClaudeListingRanker(@Value("${sheout.assistant.anthropic-api-key:}") String apiKey,
                        @Value("${sheout.assistant.model:claude-haiku-4-5-20251001}") String model,
                        @Value("${sheout.assistant.search.max-tokens:200}") long maxTokens,
                        @Value("${sheout.assistant.search.timeout-seconds:8}") long timeoutSeconds) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.client = apiKey == null || apiKey.isBlank()
                ? null
                : AnthropicOkHttpClient.builder()
                        .apiKey(apiKey)
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .maxRetries(0)
                        .build();
    }

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
    public Optional<Result> rank(String systemPrompt, String request) {
        if (client == null) {
            return Optional.empty();
        }
        try {
            StructuredMessage<Picks> response = client.beta().messages().create(MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(maxTokens)
                    .system(systemPrompt)
                    .outputConfig(Picks.class)
                    .addUserMessage(request)
                    .build());
            var usage = response.usage();
            HelpModel.Usage spent = new HelpModel.Usage(usage.inputTokens(), usage.outputTokens(),
                    usage.cacheReadInputTokens().orElse(0L), usage.cacheCreationInputTokens().orElse(0L));
            if (response.stopReason().map(r -> r.equals(BetaStopReason.REFUSAL)).orElse(false)) {
                log.warn("Marketplace search: the model declined - falling back to keyword search");
                return Optional.of(new Result(null, spent));
            }
            Optional<Picks> picks = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .findFirst();
            return Optional.of(new Result(picks.orElse(null), spent));
        } catch (AnthropicServiceException e) {
            log.warn("Marketplace search: Anthropic API error {} - {}", e.statusCode(), PhoneMasking.mask(e.getMessage()));
            return Optional.empty();
        } catch (RuntimeException e) {
            // A timeout, a network failure, or an answer that did not parse. The class only: the message can quote the reply.
            log.warn("Marketplace search: request failed ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
