package com.sheout.assistant.internal.web;

import com.sheout.assistant.internal.AssistantService;
import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.sharedkernel.ratelimit.RateLimiter;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.sharedkernel.web.ClientAddressResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * SheOut Help, for riders and partners. The app sends the conversation so
 * far (the server keeps none of it); the answer says what kind of reply it
 * is, so the app knows whether to show SOS, a hand-off to a person, or an
 * answer.
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    /** The model sees the last six messages: enough for a follow-up, and the cost of a long chat stays flat. */
    private static final int MAX_TURNS = 6;
    private static final int MAX_TOTAL_CHARS = 8000;

    private final AssistantService assistant;
    private final RateLimiter rateLimiter;
    private final ClientAddressResolver clientAddress;
    private final int perIpHourlyLimit;

    public AssistantController(AssistantService assistant, RateLimiter rateLimiter, ClientAddressResolver clientAddress,
                               @Value("${sheout.assistant.per-ip-hourly-limit:60}") int perIpHourlyLimit) {
        this.assistant = assistant;
        this.rateLimiter = rateLimiter;
        this.clientAddress = clientAddress;
        this.perIpHourlyLimit = perIpHourlyLimit;
    }

    @PostMapping("/messages")
    public ResponseEntity<AssistantService.Reply> ask(@Valid @RequestBody AskRequest request, HttpServletRequest http) {
        // Per address, before anything else: one address signing up account
        // after account to get round the per-account cap still hits this.
        // (The assistant is signed-in only; there is no logged-out use.)
        rateLimiter.tryConsume("assistant-ip:" + clientAddress.resolve(http), perIpHourlyLimit, Duration.ofHours(1))
                .orThrow("Too many messages from this network. Please try again later, or raise a support ticket.");
        CurrentAccount caller = CurrentAccountContext.get()
                .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
        if (caller.role() != AccountRole.CUSTOMER && caller.role() != AccountRole.DRIVER) {
            throw ApiException.forbidden("SheOut Help is for riders and partners");
        }
        // Bursts, beside the daily cap: nobody types six questions in ten seconds.
        rateLimiter.tryConsume("assistant:" + caller.accountId(), 6, Duration.ofMinutes(1))
                .orThrow("Please wait a moment before sending another message.");
        List<AskRequest.Message> messages = request.messages();
        if (messages.size() > MAX_TURNS) {
            messages = messages.subList(messages.size() - MAX_TURNS, messages.size());
        }
        // The model needs a conversation that starts and ends with her and alternates.
        while (!messages.isEmpty() && !"user".equals(messages.get(0).role())) {
            messages = messages.subList(1, messages.size());
        }
        if (messages.isEmpty() || !"user".equals(messages.get(messages.size() - 1).role())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONVERSATION", "The last message must be yours.");
        }
        for (int i = 1; i < messages.size(); i++) {
            if (messages.get(i).role().equals(messages.get(i - 1).role())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONVERSATION", "Messages must alternate.");
            }
        }
        if (messages.stream().mapToInt(m -> m.text().length()).sum() > MAX_TOTAL_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONVERSATION_TOO_LONG", "Please start a new conversation.");
        }
        List<AssistantService.Turn> turns = messages.stream()
                .map(m -> new AssistantService.Turn("user".equals(m.role()), m.text().trim()))
                .toList();
        return ResponseEntity.ok(assistant.ask(caller.accountId(), caller.role(), request.language(), turns));
    }

    public record AskRequest(
            @NotEmpty @Size(max = 40) List<@Valid Message> messages,
            @Pattern(regexp = "en|hi|te") String language) {

        public record Message(@NotBlank @Pattern(regexp = "user|assistant") String role,
                              @NotBlank @Size(max = 1500) String text) {
        }
    }
}
