package com.sheout.chat.internal.web;

import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.chat.ChatError;
import com.sheout.chat.ChatMessage;
import com.sheout.chat.internal.ChatService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.web.ApiException;
import com.sheout.support.SupportApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service chat for the two people on a trip. Both apps use the same
 * endpoints; who you are is taken from the token, never from the request.
 */
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/chat")
public class ChatController {

    /**
     * The number a rider or partner is given instead of each other's.
     * <p>
     * Read through the support module rather than from configuration here,
     * so there is one definition of it. It used to be hardcoded separately
     * in each app, with two different values, so support was reachable on
     * one number from the rider's app and another from the partner's.
     * Repeating the lookup in this controller would have been the same
     * mistake with a shorter fuse.
     */
    private final SupportApi supportApi;
    private final ChatService chatService;

    ChatController(ChatService chatService, SupportApi supportApi) {
        this.chatService = chatService;
        this.supportApi = supportApi;
    }

    @GetMapping
    public ResponseEntity<ThreadResponse> thread(@PathVariable UUID bookingId) {
        CurrentAccount caller = requireAuthenticated();
        Result<List<ChatMessage>, ChatError> messages = chatService.threadFor(bookingId, caller.accountId());
        if (messages.isFailure()) {
            throw toApiException(messages.error());
        }
        Result<Boolean, ChatError> open = chatService.isOpen(bookingId, caller.accountId());
        if (open.isFailure()) {
            throw toApiException(open.error());
        }
        // `open` is returned rather than inferred by the client from the
        // booking status, so there is one answer to "can I still write here"
        // and it comes from the same code that enforces it.
        return ResponseEntity.ok(new ThreadResponse(messages.value(), open.value(), supportApi.supportPhoneNumber().orElse(null)));
    }

    @PostMapping
    public ResponseEntity<ChatMessage> send(
            @PathVariable UUID bookingId,
            @Valid @RequestBody SendMessageRequest request) {
        CurrentAccount caller = requireAuthenticated();
        Result<ChatMessage, ChatError> result =
                chatService.send(bookingId, caller.accountId(), caller.role(), request.body());
        if (result.isFailure()) {
            throw toApiException(result.error());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.value());
    }

    private CurrentAccount requireAuthenticated() {
        return CurrentAccountContext.get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    private ApiException toApiException(ChatError error) {
        return switch (error) {
            // 404 for both "no such booking" and "not yours" - see ChatError.
            case BOOKING_NOT_FOUND -> ApiException.notFound("No booking found for this id");
            case CHAT_CLOSED -> new ApiException(HttpStatus.CONFLICT, "CHAT_CLOSED",
                    "This trip has ended, so the chat is read-only. Contact support if you still need help.");
            case INVALID_MESSAGE -> new ApiException(HttpStatus.BAD_REQUEST, "Bad Request",
                    "Type a message first.");
            case CONTACT_DETAILS_NOT_ALLOWED -> new ApiException(HttpStatus.BAD_REQUEST,
                    "CONTACT_DETAILS_NOT_ALLOWED",
                    "Phone numbers cannot be shared in chat. If you need to speak to someone, use Contact Support.");
        };
    }

    /**
     * The thread, whether it is still open, and the support number to offer
     * instead of a direct call. One request, because a client needs all
     * three to render the screen honestly.
     */
    public record ThreadResponse(List<ChatMessage> messages, boolean open, String supportPhoneNumber) {
    }

    public record SendMessageRequest(@NotBlank @Size(max = 1000) String body) {
    }
}
